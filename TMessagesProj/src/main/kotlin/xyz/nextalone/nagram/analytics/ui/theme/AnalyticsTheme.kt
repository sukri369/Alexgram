package xyz.nextalone.nagram.analytics.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import org.telegram.ui.ActionBar.Theme

// ─── Accent colours (same in both modes) ─────────────────────────────────────
val NeonCyan   = Color(0xFF00C8E8)
val NeonPurple = Color(0xFF9000DD)
val NeonPink   = Color(0xFFD81B7C)
val NeonGreen  = Color(0xFF00C853)
val NeonOrange = Color(0xFFFF6D00)
val NeonRed    = Color(0xFFDD1430)

// ─── Adaptive colour set ───────────────────────────────────────────────────────
@Immutable
data class AnalyticsColors(
    val bgPrimary: Color,
    val bgCard: Color,
    val bgCardAlt: Color,
    val bgHero: Brush,
    val bgScreen: Brush,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val accentGradient: Brush,
    val isDark: Boolean
)

val DarkAnalyticsColors = AnalyticsColors(
    bgPrimary   = Color(0xFF0D0F1A),
    bgCard      = Color(0xFF161927),
    bgCardAlt   = Color(0xFF1C2033),
    bgHero      = Brush.verticalGradient(listOf(Color(0xFF1A1F3C), Color(0xFF0D0F1A))),
    bgScreen    = Brush.verticalGradient(listOf(Color(0xFF0D0F1A), Color(0xFF0A0C16))),
    textPrimary = Color(0xFFEEF0F8),
    textSecondary = Color(0xFF8A94B0),
    border      = Color(0xFF2A2F4A),
    accentGradient = Brush.linearGradient(listOf(NeonCyan, NeonPurple)),
    isDark      = true
)

val LightAnalyticsColors = AnalyticsColors(
    bgPrimary   = Color(0xFFF2F4FB),
    bgCard      = Color(0xFFFFFFFF),
    bgCardAlt   = Color(0xFFECEFF8),
    bgHero      = Brush.verticalGradient(listOf(Color(0xFFE8ECFF), Color(0xFFF2F4FB))),
    bgScreen    = Brush.verticalGradient(listOf(Color(0xFFF2F4FB), Color(0xFFEAECF8))),
    textPrimary = Color(0xFF0D0F1A),
    textSecondary = Color(0xFF5A6080),
    border      = Color(0xFFD0D5EA),
    accentGradient = Brush.linearGradient(listOf(Color(0xFF0099BB), Color(0xFF6600BB))),
    isDark      = false
)

// ─── CompositionLocal ──────────────────────────────────────────────────────────
val LocalAnalyticsColors = staticCompositionLocalOf { DarkAnalyticsColors }

/** Root wrapper — auto-selects dark / light palette based on system setting */
@Composable
fun AnalyticsTheme(
    darkTheme: Boolean = Theme.isCurrentThemeDark(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkAnalyticsColors else LightAnalyticsColors
    CompositionLocalProvider(LocalAnalyticsColors provides colors) {
        content()
    }
}
