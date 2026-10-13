package com.example.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Who is using the app right now. */
sealed interface AccountState {
    /** Never chose: show the welcome / sign-in screen. */
    data object SignedOut : AccountState

    /** Uses the app without an account. Everything stays on this device. */
    data object Guest : AccountState

    data class SignedIn(val uid: String, val email: String?, val displayName: String?) : AccountState
}

/** What went wrong, in user terms. The UI maps each value to a string resource. */
enum class AuthError {
    INVALID_CREDENTIALS, INVALID_EMAIL, EMAIL_IN_USE, WEAK_PASSWORD, USER_DISABLED,
    NETWORK, TOO_MANY_REQUESTS, UNAVAILABLE, UNKNOWN
}

sealed interface AuthOutcome {
    data object Success : AuthOutcome
    data class Failure(val error: AuthError) : AuthOutcome
}

/**
 * Single source of truth for the account and guest mode.
 *
 * Guest mode is a local flag ("guest_mode" in focusflow_prefs) so the app works fully offline.
 * When possible a guest is also signed in anonymously in the background; creating an account
 * later links the email to that same user, and the guest's local sessions and cards are then
 * uploaded by the normal sync.
 */
class AuthRepository(
    context: Context,
    private val appScope: CoroutineScope,
    /** Called after a real (non-guest) sign-in or account creation, off the main thread. */
    private val onSignedIn: suspend () -> Unit
) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val auth: FirebaseAuth? = runCatching { Firebase.auth }.onFailure { Log.w(TAG, "Firebase Auth unavailable", it) }.getOrNull()
    private val db: FirebaseFirestore? = runCatching { Firebase.firestore }.getOrNull()

    private val _state = MutableStateFlow(compute())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    // Held in a field: SharedPreferences keeps listeners in a weak map.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == KEY_GUEST_MODE) refresh()
    }

    init {
        auth?.addAuthStateListener { refresh() }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    /** False when Firebase couldn't start (missing config, broken Play services): only guest mode works. */
    val isAvailable: Boolean get() = auth != null

    /** True when the user may skip the welcome screen. */
    val canEnterApp: Boolean get() = state.value != AccountState.SignedOut

    fun refresh() {
        _state.value = compute()
    }

    private fun compute(): AccountState {
        val user = runCatching { auth?.currentUser }.getOrNull()
        return when {
            user != null && !user.isAnonymous -> AccountState.SignedIn(user.uid, user.email, user.displayName)
            prefs.getBoolean(KEY_GUEST_MODE, false) || user?.isAnonymous == true -> AccountState.Guest
            else -> AccountState.SignedOut
        }
    }

    suspend fun signIn(email: String, password: String): AuthOutcome = guarded {
        val auth = auth ?: return AuthOutcome.Failure(AuthError.UNAVAILABLE)
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        afterRealSignIn()
    }

    /**
     * Creates an email account. A guest who is signed in anonymously keeps the same user id
     * (the email is linked to it), so nothing is lost.
     */
    suspend fun createAccount(email: String, password: String, username: String): AuthOutcome = guarded {
        val auth = auth ?: return AuthOutcome.Failure(AuthError.UNAVAILABLE)
        val cleanEmail = email.trim()
        val name = username.trim()
        val current = auth.currentUser
        val user = if (current != null && current.isAnonymous) {
            current.linkWithCredential(EmailAuthProvider.getCredential(cleanEmail, password)).await().user
        } else {
            auth.createUserWithEmailAndPassword(cleanEmail, password).await().user
        } ?: return AuthOutcome.Failure(AuthError.UNKNOWN)

        runCatching { user.updateProfile(userProfileChangeRequest { displayName = name }).await() }
            .onFailure { Log.w(TAG, "Display name not saved", it) }

        // The account exists at this point; a failed profile write must not block the user.
        // Stats fields start at 0 so the leaderboard query (ordered by a stats field) includes
        // the new entry; the first sync then publishes the real values.
        runCatching {
            val firestore = db ?: return@runCatching
            val uid = user.uid
            val stats = mapOf("treeCount" to 0, "totalMinutes" to 0, "points" to 0, "currentStreak" to 0)
            firestore.collection("users").document(uid).set(
                mapOf(
                    "uid" to uid,
                    "username" to name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "currentRadio" to "",
                    "friends" to emptyList<String>()
                ) + stats,
                SetOptions.merge()
            ).await()
            firestore.collection("leaderboard").document(uid).set(
                mapOf("uid" to uid, "username" to name) + stats,
                SetOptions.merge()
            ).await()
        }.onFailure { Log.w(TAG, "Profile documents not created", it) }

        afterRealSignIn()
    }

    /** Firebase doesn't reveal whether the address has an account; the UI says so neutrally. */
    suspend fun sendPasswordReset(email: String): AuthOutcome = guarded {
        val auth = auth ?: return AuthOutcome.Failure(AuthError.UNAVAILABLE)
        auth.sendPasswordResetEmail(email.trim()).await()
        AuthOutcome.Success
    }

    /** Enters the app immediately; the anonymous sign-in happens in the background and may fail. */
    fun continueAsGuest() {
        prefs.edit().putBoolean(KEY_GUEST_MODE, true).apply()
        refresh()
        val auth = auth ?: return
        if (auth.currentUser != null) return
        appScope.launch {
            runCatching { auth.signInAnonymously().await() }
                .onFailure { Log.i(TAG, "Anonymous sign-in skipped: ${it.message}") }
            refresh()
        }
    }

    /** Signs out and leaves guest mode, so the welcome screen shows next time. */
    fun signOut() {
        runCatching { auth?.signOut() }.onFailure { Log.e(TAG, "Sign out failed", it) }
        prefs.edit().putBoolean(KEY_GUEST_MODE, false).apply()
        refresh()
    }

    private fun afterRealSignIn(): AuthOutcome {
        prefs.edit().putBoolean(KEY_GUEST_MODE, false).apply()
        refresh()
        appScope.launch {
            runCatching { onSignedIn() }.onFailure { Log.w(TAG, "Post sign-in sync failed", it) }
        }
        return AuthOutcome.Success
    }

    /** Runs a Firebase call and turns any exception into a user-facing [AuthError]. */
    private inline fun guarded(block: () -> AuthOutcome): AuthOutcome = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Auth request failed: ${e.javaClass.simpleName}")
        AuthOutcome.Failure(e.toAuthError())
    }

    companion object {
        private const val TAG = "AuthRepository"
        const val PREFS = "focusflow_prefs"
        const val KEY_GUEST_MODE = "guest_mode"

        /** Order matters: some Firebase exceptions extend others. */
        fun Throwable.toAuthError(): AuthError = when (this) {
            is FirebaseAuthWeakPasswordException -> AuthError.WEAK_PASSWORD
            is FirebaseAuthUserCollisionException -> AuthError.EMAIL_IN_USE
            is FirebaseAuthInvalidUserException ->
                if (errorCode == "ERROR_USER_DISABLED") AuthError.USER_DISABLED else AuthError.INVALID_CREDENTIALS
            is FirebaseAuthInvalidCredentialsException ->
                if (errorCode == "ERROR_INVALID_EMAIL") AuthError.INVALID_EMAIL else AuthError.INVALID_CREDENTIALS
            is FirebaseNetworkException -> AuthError.NETWORK
            is FirebaseTooManyRequestsException -> AuthError.TOO_MANY_REQUESTS
            is FirebaseAuthException -> when (errorCode) {
                "ERROR_OPERATION_NOT_ALLOWED", "ERROR_APP_NOT_AUTHORIZED" -> AuthError.UNAVAILABLE
                "ERROR_CREDENTIAL_ALREADY_IN_USE", "ERROR_EMAIL_ALREADY_IN_USE" -> AuthError.EMAIL_IN_USE
                else -> AuthError.UNKNOWN
            }
            else -> AuthError.UNKNOWN
        }
    }
}
