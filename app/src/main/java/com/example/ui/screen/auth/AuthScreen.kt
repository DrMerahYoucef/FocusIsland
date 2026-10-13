package com.example.ui.screen.auth

import com.example.ui.components.autofillEmail
import com.example.ui.components.autofillName
import com.example.ui.components.autofillPassword
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.auth.AuthError
import com.example.ui.designsystem.BannerTone
import com.example.ui.designsystem.InfoBanner
import com.example.ui.designsystem.ThemePreviews
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.Spacing
import com.example.ui.theme.ThemeMode

/**
 * Welcome / sign-in screen.
 *
 * First run: sign in, create an account, or continue without one (guest mode).
 * Opened by a guest from inside the app ([onBack] non-null): create an account or sign in;
 * the guest option is hidden because they already use it.
 */
@Composable
fun AuthScreen(
    onFinished: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: AuthViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    AuthContent(
        state = state,
        onBack = onBack,
        onModeChange = viewModel::setMode,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onUsernameChange = viewModel::onUsernameChange,
        onSubmit = viewModel::submit,
        onForgotPassword = viewModel::openReset,
        onContinueAsGuest = viewModel::continueAsGuest
    )

    state.reset?.let { reset ->
        ResetPasswordDialog(
            state = reset,
            onEmailChange = viewModel::onResetEmailChange,
            onSend = viewModel::sendReset,
            onDismiss = viewModel::closeReset
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthContent(
    state: AuthUiState,
    onBack: (() -> Unit)?,
    onModeChange: (AuthMode) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
    onContinueAsGuest: () -> Unit
) {
    val creating = state.mode == AuthMode.CREATE_ACCOUNT
    val formEnabled = state.accountsAvailable && !state.submitting
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current

    val nameFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    // Move focus to the first field that needs fixing, so TalkBack reads its error.
    LaunchedEffect(state.usernameError, state.emailError, state.passwordError) {
        when {
            state.usernameError != null -> nameFocus.requestFocus()
            state.emailError != null -> emailFocus.requestFocus()
            state.passwordError != null -> passwordFocus.requestFocus()
        }
    }
    LaunchedEffect(state.error) {
        if (state.error != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val submit = {
        keyboard?.hide()
        focusManager.clearFocus()
        onSubmit()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        0.45f to MaterialTheme.colorScheme.background
                    )
                )
                .padding(padding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = Spacing.formMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BrandHeader(isUpgrade = state.isUpgrade)
                Spacer(Modifier.height(Spacing.xl))

                if (!state.accountsAvailable) {
                    InfoBanner(text = stringResource(R.string.auth_unavailable_banner), tone = BannerTone.Warning)
                    Spacer(Modifier.height(Spacing.md))
                }

                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AuthMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.mode == mode,
                            onClick = { onModeChange(mode) },
                            enabled = !state.submitting,
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = AuthMode.entries.size)
                        ) {
                            Text(stringResource(if (mode == AuthMode.SIGN_IN) R.string.auth_tab_sign_in else R.string.auth_tab_create))
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.lg))

                Column(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    AnimatedVisibility(
                        visible = creating,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        OutlinedTextField(
                            value = state.username,
                            onValueChange = onUsernameChange,
                            label = { Text(stringResource(R.string.auth_field_name)) },
                            leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null) },
                            supportingText = {
                                Text(state.usernameError?.let { fieldErrorText(it) } ?: stringResource(R.string.auth_field_name_support))
                            },
                            isError = state.usernameError != null,
                            enabled = formEnabled,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(onNext = { emailFocus.requestFocus() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus).autofillName(onUsernameChange)
                        )
                    }

                    OutlinedTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        label = { Text(stringResource(R.string.auth_field_email)) },
                        leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                        supportingText = state.emailError?.let { error -> { Text(fieldErrorText(error)) } },
                        isError = state.emailError != null,
                        enabled = formEnabled,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(emailFocus).autofillEmail(onEmailChange)
                    )

                    OutlinedTextField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        label = { Text(stringResource(R.string.auth_field_password)) },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = stringResource(if (showPassword) R.string.auth_hide_password else R.string.auth_show_password)
                                )
                            }
                        },
                        supportingText = when (val passwordError = state.passwordError) {
                            null -> if (creating) {
                                { Text(pluralStringResource(R.plurals.auth_password_hint, AuthViewModel.MIN_PASSWORD, AuthViewModel.MIN_PASSWORD)) }
                            } else null
                            else -> {
                                { Text(fieldErrorText(passwordError)) }
                            }
                        },
                        isError = state.passwordError != null,
                        enabled = formEnabled,
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus).autofillPassword(onPasswordChange)
                    )

                    if (!creating) {
                        TextButton(
                            onClick = onForgotPassword,
                            enabled = formEnabled,
                            modifier = Modifier.align(Alignment.End)
                        ) { Text(stringResource(R.string.auth_forgot_password)) }
                    }
                }

                AnimatedVisibility(visible = state.error != null) {
                    val error = state.error ?: AuthError.UNKNOWN
                    InfoBanner(
                        text = stringResource(error.messageRes()),
                        tone = if (error == AuthError.NETWORK) BannerTone.Warning else BannerTone.Error,
                        actionLabel = if (error == AuthError.EMAIL_IN_USE && creating) stringResource(R.string.auth_switch_to_sign_in) else null,
                        onAction = { onModeChange(AuthMode.SIGN_IN) },
                        modifier = Modifier
                            .padding(top = Spacing.sm)
                            .semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }

                Spacer(Modifier.height(Spacing.lg))
                val submittingLabel = stringResource(R.string.auth_submitting)
                Button(
                    onClick = submit,
                    enabled = formEnabled,
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.buttonHeight)
                        .then(if (state.submitting) Modifier.semantics { contentDescription = submittingLabel } else Modifier)
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(
                            strokeWidth = Spacing.progressStroke,
                            color = LocalContentColor.current,
                            modifier = Modifier.size(Spacing.iconSm)
                        )
                    } else {
                        Text(stringResource(if (creating) R.string.auth_submit_create else R.string.auth_submit_sign_in))
                    }
                }

                if (!state.isUpgrade) {
                    Spacer(Modifier.height(Spacing.lg))
                    OrDivider()
                    Spacer(Modifier.height(Spacing.lg))
                    OutlinedButton(
                        onClick = onContinueAsGuest,
                        enabled = !state.submitting,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.buttonHeight)
                    ) { Text(stringResource(R.string.auth_guest)) }
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = stringResource(R.string.auth_guest_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandHeader(isUpgrade: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(Spacing.illustration)
        ) {
            // The launcher artwork (island and tree) doubles as the brand mark. Decorative.
            Image(painter = painterResource(R.drawable.ic_launcher_foreground), contentDescription = null)
        }
        Text(
            text = stringResource(if (isUpgrade) R.string.auth_upgrade_title else R.string.auth_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(if (isUpgrade) R.string.auth_upgrade_body else R.string.auth_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.auth_or),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.sm)
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun ResetPasswordDialog(
    state: ResetPasswordState,
    onEmailChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit
) {
    if (state.sent) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Rounded.MarkEmailRead, contentDescription = null) },
            title = { Text(stringResource(R.string.auth_reset_sent_title)) },
            text = { Text(stringResource(R.string.auth_reset_sent_body, state.email.trim())) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } }
        )
        return
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = { if (!state.sending) onDismiss() },
        title = { Text(stringResource(R.string.auth_reset_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.auth_reset_body))
                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = { Text(stringResource(R.string.auth_field_email)) },
                    supportingText = state.emailError?.let { error -> { Text(fieldErrorText(error)) } },
                    isError = state.emailError != null,
                    enabled = !state.sending,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).autofillEmail(onEmailChange)
                )
                state.error?.let { error ->
                    Text(
                        text = stringResource(error.messageRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSend, enabled = !state.sending) {
                if (state.sending) {
                    CircularProgressIndicator(strokeWidth = Spacing.progressStroke, modifier = Modifier.size(Spacing.iconSm))
                } else {
                    Text(stringResource(R.string.auth_reset_send))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.sending) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun fieldErrorText(error: FieldError): String = when (error) {
    FieldError.EMAIL_REQUIRED -> stringResource(R.string.auth_error_email_required)
    FieldError.EMAIL_INVALID -> stringResource(R.string.auth_error_email_invalid)
    FieldError.PASSWORD_REQUIRED -> stringResource(R.string.auth_error_password_required)
    FieldError.PASSWORD_SHORT ->
        pluralStringResource(R.plurals.auth_error_min_chars, AuthViewModel.MIN_PASSWORD, AuthViewModel.MIN_PASSWORD)
    FieldError.NAME_REQUIRED -> stringResource(R.string.auth_error_name_required)
    FieldError.NAME_SHORT ->
        pluralStringResource(R.plurals.auth_error_min_chars, AuthViewModel.MIN_NAME, AuthViewModel.MIN_NAME)
    FieldError.NAME_LONG ->
        pluralStringResource(R.plurals.auth_error_max_chars, AuthViewModel.MAX_NAME, AuthViewModel.MAX_NAME)
}

private fun AuthError.messageRes(): Int = when (this) {
    AuthError.INVALID_CREDENTIALS -> R.string.auth_error_invalid_credentials
    AuthError.INVALID_EMAIL -> R.string.auth_error_email_invalid
    AuthError.EMAIL_IN_USE -> R.string.auth_error_email_in_use
    AuthError.WEAK_PASSWORD -> R.string.auth_error_weak_password
    AuthError.USER_DISABLED -> R.string.auth_error_user_disabled
    AuthError.NETWORK -> R.string.auth_error_network
    AuthError.TOO_MANY_REQUESTS -> R.string.auth_error_too_many_requests
    AuthError.UNAVAILABLE -> R.string.auth_error_unavailable
    AuthError.UNKNOWN -> R.string.auth_error_unknown
}

// ---------------------------------------------------------------- previews

private val noop: () -> Unit = {}

@Composable
private fun PreviewAuth(state: AuthUiState, onBack: (() -> Unit)? = null) {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        AuthContent(
            state = state,
            onBack = onBack,
            onModeChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onUsernameChange = {},
            onSubmit = noop,
            onForgotPassword = noop,
            onContinueAsGuest = noop
        )
    }
}

@ThemePreviews
@Composable
private fun AuthSignInPreview() = PreviewAuth(AuthUiState(email = "sara@example.com"))

@ThemePreviews
@Composable
private fun AuthCreateErrorsPreview() = PreviewAuth(
    AuthUiState(
        mode = AuthMode.CREATE_ACCOUNT,
        username = "S",
        email = "sara@",
        password = "123",
        usernameError = FieldError.NAME_SHORT,
        emailError = FieldError.EMAIL_INVALID,
        passwordError = FieldError.PASSWORD_SHORT
    )
)

@ThemePreviews
@Composable
private fun AuthUpgradePreview() = PreviewAuth(
    AuthUiState(mode = AuthMode.CREATE_ACCOUNT, isUpgrade = true, error = AuthError.EMAIL_IN_USE),
    onBack = noop
)

@Preview(name = "Reset sent", showBackground = true)
@Composable
private fun ResetSentPreview() {
    FocusIslandTheme(themeMode = ThemeMode.LIGHT) {
        ResetPasswordDialog(ResetPasswordState(email = "sara@example.com", sent = true), {}, noop, noop)
    }
}
