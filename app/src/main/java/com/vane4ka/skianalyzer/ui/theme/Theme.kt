/**
 * Top-level Material 3 theme wrapper.
 *
 * [MyApplicationTheme] is the single Composable every screen wraps its content
 * in (see [MainActivity] and the `@Preview` blocks in [AuthScreen],
 * [MistakesScreen], etc.). It plugs together:
 *   * `LightColorScheme` — the project palette from `Color.kt` mapped onto
 *     Material 3 color roles (primary, surface, error, onSurfaceVariant…).
 *   * `AppTypography`    — defined in `Type.kt`.
 *   * `AppShapes`        — defined in `Shape.kt`.
 *
 * The app is light-only on purpose; there is no dark-mode color scheme.
 */

package com.vane4ka.skianalyzer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = CardSurface,
    secondary = AccentCyan,
    onSecondary = CardSurface,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = UploadTint,
    onSurfaceVariant = TextSecondary,
    outline = DividerLight,
    error = IssueRed,
    onError = CardSurface
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}