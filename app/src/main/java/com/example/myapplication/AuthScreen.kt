package com.example.myapplication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownhillSkiing
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.AccentCyan
import com.example.myapplication.ui.theme.AppBackground
import com.example.myapplication.ui.theme.CardSurface
import com.example.myapplication.ui.theme.DividerLight
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.theme.PrimaryBlue
import com.example.myapplication.ui.theme.PrimaryBlueDark
import com.example.myapplication.ui.theme.TextMuted
import com.example.myapplication.ui.theme.TextPrimary
import com.example.myapplication.ui.theme.TextSecondary

enum class AuthMode { LOGIN, REGISTER }

@Composable
fun AuthScreen(
    onSubmit: (mode: AuthMode, username: String, email: String, password: String) -> Unit = { _, _, _, _ -> },
    error: AuthError? = null,
    isSubmitting: Boolean = false,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val isRegister = mode == AuthMode.REGISTER
    val canSubmit = email.isNotBlank() && password.length >= 8 &&
        (!isRegister || username.length >= 3)

    val errorText: String? = when (error) {
        null -> null
        AuthError.InvalidCredentials -> stringResource(R.string.auth_error_invalid_credentials)
        AuthError.UserExists -> stringResource(R.string.auth_error_user_exists)
        is AuthError.ServerError -> stringResource(R.string.auth_error_server, error.code)
        is AuthError.NetworkError -> stringResource(R.string.auth_error_network, error.detail)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(PrimaryBlue, AccentCyan))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.DownhillSkiing,
                contentDescription = null,
                tint = CardSurface,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(
                if (isRegister) R.string.auth_title_register else R.string.auth_title_login
            ),
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(
                if (isRegister) R.string.auth_subtitle_register else R.string.auth_subtitle_login
            ),
            color = TextSecondary,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(28.dp))
        ModeToggle(mode = mode, onModeChange = { mode = it })

        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardSurface)
                .padding(18.dp)
        ) {
            if (isRegister) {
                FieldLabel(stringResource(R.string.auth_field_username))
                Spacer(Modifier.height(6.dp))
                AuthTextField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = stringResource(R.string.auth_field_username_hint),
                    keyboardType = KeyboardType.Text
                )
                Spacer(Modifier.height(14.dp))
            }

            FieldLabel(stringResource(R.string.auth_field_email))
            Spacer(Modifier.height(6.dp))
            AuthTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = stringResource(R.string.auth_field_email_hint),
                keyboardType = KeyboardType.Email
            )

            Spacer(Modifier.height(14.dp))
            FieldLabel(stringResource(R.string.auth_field_password))
            Spacer(Modifier.height(6.dp))
            AuthTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = stringResource(R.string.auth_field_password_hint),
                keyboardType = KeyboardType.Password,
                visualTransformation = if (showPassword)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                trailing = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(
                                if (showPassword) R.string.auth_password_hide
                                else R.string.auth_password_show
                            ),
                            tint = TextMuted
                        )
                    }
                }
            )

            if (errorText != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = errorText,
                    color = Color(0xFFDC2626),
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = stringResource(
                    when {
                        isSubmitting && isRegister -> R.string.auth_button_register_busy
                        isSubmitting               -> R.string.auth_button_login_busy
                        isRegister                 -> R.string.auth_button_register
                        else                       -> R.string.auth_button_login
                    }
                ),
                enabled = canSubmit && !isSubmitting,
                onClick = { onSubmit(mode, username, email, password) }
            )
        }

        Spacer(Modifier.height(16.dp))
        Row {
            Text(
                text = stringResource(
                    if (isRegister) R.string.auth_have_account else R.string.auth_no_account
                ),
                color = TextSecondary,
                fontSize = 13.sp
            )
            Text(
                text = stringResource(
                    if (isRegister) R.string.auth_switch_to_login else R.string.auth_switch_to_register
                ),
                color = PrimaryBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    mode = if (isRegister) AuthMode.LOGIN else AuthMode.REGISTER
                }
            )
        }
    }
}

@Composable
private fun ModeToggle(mode: AuthMode, onModeChange: (AuthMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DividerLight)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ToggleChip(
            label = stringResource(R.string.auth_mode_login),
            selected = mode == AuthMode.LOGIN,
            onClick = { onModeChange(AuthMode.LOGIN) },
            modifier = Modifier.weight(1f)
        )
        ToggleChip(
            label = stringResource(R.string.auth_mode_register),
            selected = mode == AuthMode.REGISTER,
            onClick = { onModeChange(AuthMode.REGISTER) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) CardSurface else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(text = placeholder, color = TextMuted, fontSize = 14.sp)
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        trailingIcon = trailing,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryBlue,
            unfocusedBorderColor = DividerLight,
            focusedContainerColor = AppBackground,
            unfocusedContainerColor = AppBackground
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bg = if (enabled)
        Brush.horizontalGradient(listOf(PrimaryBlueDark, AccentCyan))
    else
        Brush.horizontalGradient(listOf(DividerLight, DividerLight))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) CardSurface else TextMuted,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Preview(showBackground = true, name = "Auth — Login")
@Composable
private fun AuthScreenLoginPreview() {
    MyApplicationTheme {
        AuthScreen()
    }
}

@Preview(showBackground = true, name = "Auth — Error state")
@Composable
private fun AuthScreenErrorPreview() {
    MyApplicationTheme {
        AuthScreen(error = AuthError.UserExists)
    }
}