package com.example.ui.screen.auth

import android.app.Application
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.FocusFlowApplication
import com.example.data.auth.AccountState
import com.example.data.auth.AuthError
import com.example.data.auth.AuthOutcome
import com.example.data.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode(val key: String) {
    SIGN_IN("signin"), CREATE_ACCOUNT("create");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: SIGN_IN
    }
}

/** Inline field errors, shown under the field that needs fixing (text chosen by the screen). */
enum class FieldError {
    EMAIL_REQUIRED, EMAIL_INVALID, PASSWORD_REQUIRED, PASSWORD_SHORT, NAME_REQUIRED, NAME_SHORT, NAME_LONG
}

data class ResetPasswordState(
    val email: String,
    val emailError: FieldError? = null,
    val sending: Boolean = false,
    val sent: Boolean = false,
    val error: AuthError? = null
)

data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val username: String = "",
    val emailError: FieldError? = null,
    val passwordError: FieldError? = null,
    val usernameError: FieldError? = null,
    val submitting: Boolean = false,
    val error: AuthError? = null,
    /** A guest opened this screen from inside the app to create an account or sign in. */
    val isUpgrade: Boolean = false,
    /** Firebase couldn't start on this device; only "Continue without an account" works. */
    val accountsAvailable: Boolean = true,
    val reset: ResetPasswordState? = null,
    /** Set once the user may enter the app; the screen then navigates exactly once. */
    val finished: Boolean = false
)

/**
 * Sign in, create an account, reset the password, or continue as a guest.
 * Mode, email and name survive process death; the password deliberately does not.
 */
class AuthViewModel(
    application: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository: AuthRepository = FocusFlowApplication.instance.authRepository

    private val _state = MutableStateFlow(
        AuthUiState(
            mode = AuthMode.from(savedState.get<String>(KEY_MODE)),
            email = savedState.get<String>(KEY_EMAIL).orEmpty(),
            username = savedState.get<String>(KEY_NAME).orEmpty(),
            isUpgrade = repository.state.value == AccountState.Guest,
            accountsAvailable = repository.isAvailable
        )
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun setMode(mode: AuthMode) {
        savedState[KEY_MODE] = mode.key
        _state.update {
            it.copy(mode = mode, error = null, emailError = null, passwordError = null, usernameError = null)
        }
    }

    fun onEmailChange(value: String) {
        savedState[KEY_EMAIL] = value
        _state.update { it.copy(email = value, emailError = null, error = null) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, passwordError = null, error = null) }
    }

    fun onUsernameChange(value: String) {
        savedState[KEY_NAME] = value
        _state.update { it.copy(username = value, usernameError = null, error = null) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun submit() {
        val current = _state.value
        if (current.submitting) return
        val creating = current.mode == AuthMode.CREATE_ACCOUNT
        val emailError = validateEmail(current.email)
        val passwordError = validatePassword(current.password, creating)
        val usernameError = if (creating) validateName(current.username) else null
        if (emailError != null || passwordError != null || usernameError != null) {
            _state.update { it.copy(emailError = emailError, passwordError = passwordError, usernameError = usernameError) }
            return
        }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val outcome = if (creating) {
                repository.createAccount(current.email, current.password, current.username)
            } else {
                repository.signIn(current.email, current.password)
            }
            _state.update {
                when (outcome) {
                    AuthOutcome.Success -> it.copy(submitting = false, password = "", finished = true)
                    is AuthOutcome.Failure -> it.copy(submitting = false, error = outcome.error)
                }
            }
        }
    }

    fun continueAsGuest() {
        repository.continueAsGuest()
        _state.update { it.copy(finished = true) }
    }

    // ------------------------------------------------------------- password reset

    fun openReset() = _state.update { it.copy(reset = ResetPasswordState(email = it.email.trim())) }

    fun closeReset() = _state.update { it.copy(reset = null) }

    fun onResetEmailChange(value: String) = _state.update { s ->
        s.copy(reset = s.reset?.copy(email = value, emailError = null, error = null))
    }

    fun sendReset() {
        val reset = _state.value.reset ?: return
        if (reset.sending) return
        val emailError = validateEmail(reset.email)
        if (emailError != null) {
            _state.update { it.copy(reset = reset.copy(emailError = emailError)) }
            return
        }
        _state.update { it.copy(reset = reset.copy(sending = true, error = null)) }
        viewModelScope.launch {
            val outcome = repository.sendPasswordReset(reset.email)
            _state.update { s ->
                val r = s.reset ?: return@update s
                when (outcome) {
                    AuthOutcome.Success -> s.copy(reset = r.copy(sending = false, sent = true))
                    is AuthOutcome.Failure -> s.copy(reset = r.copy(sending = false, error = outcome.error))
                }
            }
        }
    }

    companion object {
        private const val KEY_MODE = "mode" // also the navigation argument name
        private const val KEY_EMAIL = "auth_email"
        private const val KEY_NAME = "auth_name"

        const val MIN_PASSWORD = 6 // Firebase's own minimum
        const val MIN_NAME = 2
        const val MAX_NAME = 24

        fun validateEmail(email: String): FieldError? {
            val trimmed = email.trim()
            return when {
                trimmed.isEmpty() -> FieldError.EMAIL_REQUIRED
                !Patterns.EMAIL_ADDRESS.matcher(trimmed).matches() -> FieldError.EMAIL_INVALID
                else -> null
            }
        }

        /** Length is only checked when creating an account; old accounts may predate any rule. */
        fun validatePassword(password: String, creating: Boolean): FieldError? = when {
            password.isEmpty() -> FieldError.PASSWORD_REQUIRED
            creating && password.length < MIN_PASSWORD -> FieldError.PASSWORD_SHORT
            else -> null
        }

        fun validateName(name: String): FieldError? {
            val length = name.trim().length
            return when {
                length == 0 -> FieldError.NAME_REQUIRED
                length < MIN_NAME -> FieldError.NAME_SHORT
                length > MAX_NAME -> FieldError.NAME_LONG
                else -> null
            }
        }
    }
}
