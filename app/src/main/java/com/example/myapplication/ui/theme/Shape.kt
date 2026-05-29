/**
 * Material 3 [Shapes] for the app — rounded-corner radii for each size
 * bucket (`extraSmall` 8.dp → `extraLarge` 28.dp).
 *
 * Plugged into [MyApplicationTheme]; most call sites use literal
 * `RoundedCornerShape(...)` instead of these tokens, so this object is mainly
 * a default for Material 3 components that pick up the theme automatically.
 */

package com.example.myapplication.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)