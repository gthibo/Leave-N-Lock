package io.github.gthibo.leavenlock.ui.login

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.gthibo.leavenlock.R
import io.github.gthibo.leavenlock.domain.LogLevel
import io.github.gthibo.leavenlock.ui.components.ButtonVariant
import io.github.gthibo.leavenlock.ui.components.Hairline
import io.github.gthibo.leavenlock.ui.components.PgButton
import io.github.gthibo.leavenlock.ui.components.PgCard
import io.github.gthibo.leavenlock.ui.components.PgField
import io.github.gthibo.leavenlock.ui.components.PgProgress
import io.github.gthibo.leavenlock.ui.components.PgTopBar
import io.github.gthibo.leavenlock.ui.components.mutedText
import io.github.gthibo.leavenlock.ui.theme.porcelain.Porcelain
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainSpace
import io.github.gthibo.leavenlock.ui.theme.porcelain.PorcelainText

/**
 * Sign-in — one form, one job: email + password (+ optional 4-digit lock PIN).
 *
 * A live log strip surfaces each step.
 *
 * Security: FLAG_SECURE blocks screenshots/recents thumbnails while credentials are visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onSuccess: (String) -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val log by viewModel.log.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(state) {
        (state as? LoginState.Success)?.let { onSuccess(it.email) }
    }

    Scaffold(
        containerColor = Porcelain.colors.surface,
        topBar = {
            PgTopBar(
                title = stringResource(R.string.login_title_bluelink),
                onBack = onBack,
                actions = {
                    if (log.isNotEmpty()) {
                        PgButton(
                            text = stringResource(R.string.login_copy_log),
                            onClick = {
                                val text = log.asReversed().joinToString("\n") { it.message }
                                clipboard.setText(AnnotatedString(text))
                            },
                            variant = ButtonVariant.Ghost,
                        )
                        PgButton(text = stringResource(R.string.login_clear), onClick = { viewModel.clearLog() }, variant = ButtonVariant.Ghost)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state is LoginState.InProgress) {
                PgProgress()
            }

            if (log.isNotEmpty()) {
                PgCard(
                    modifier = Modifier
                        .padding(horizontal = PorcelainSpace.screenMargin, vertical = PorcelainSpace.s2),
                    cut = 8.dp,
                    contentPadding = PaddingValues(PorcelainSpace.s3),
                    spacing = 2.dp,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        log.take(30).forEach { entry ->
                            Text(
                                entry.message,
                                style = PorcelainText.data.copy(fontSize = 11.sp, lineHeight = 16.sp),
                                color = if (entry.level == LogLevel.ERROR) Porcelain.colors.danger else mutedText,
                            )
                        }
                    }
                }
            }

            SignInForm(
                email = email,
                onEmailChange = { email = it },
                password = password,
                onPasswordChange = { password = it },
                pin = pin,
                onPinChange = { pin = it.filter { c -> c.isDigit() }.take(6) },
                state = state,
                onSignIn = {
                    viewModel.clearLog()
                    viewModel.logInfo("Sign in requested for ${email.trim()}")
                    viewModel.onSignIn(email, password, pin)
                },
                onDemo = { viewModel.demoLogin() },
            )
        }
    }
}

@Composable
private fun SignInForm(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    pin: String,
    onPinChange: (String) -> Unit,
    state: LoginState,
    onSignIn: () -> Unit,
    onDemo: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(PorcelainSpace.screenMargin),
        verticalArrangement = Arrangement.spacedBy(PorcelainSpace.s4),
    ) {
        PgCard(title = stringResource(R.string.login_title_bluelink)) {
            Text(stringResource(R.string.login_intro), style = PorcelainText.body, color = mutedText)

            PgField(
                label = stringResource(R.string.login_email),
                value = email,
                onValueChange = onEmailChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            PgField(
                label = stringResource(R.string.login_password),
                value = password,
                onValueChange = onPasswordChange,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            PgField(
                label = stringResource(R.string.login_pin),
                value = pin,
                onValueChange = onPinChange,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )

            (state as? LoginState.Error)?.let {
                Text(it.message, style = PorcelainText.body, color = Porcelain.colors.danger)
            }

            PgButton(
                text = stringResource(R.string.login_sign_in),
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth(),
                enabled = email.isNotBlank() && password.isNotBlank() && state !is LoginState.InProgress,
            )

            Hairline()

            PgButton(
                text = stringResource(R.string.login_demo),
                onClick = onDemo,
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Tonal,
            )
        }
    }
}
