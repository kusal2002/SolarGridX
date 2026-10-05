package com.kusal.solargridxmobile.ui.theme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Every screen currently uses light surfaces. Keep all menus and dialogs on the same palette.
private val SolarColors = lightColorScheme(
    primary = Color(0xFF15803D), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCFCE7), onPrimaryContainer = Color(0xFF14532D),
    secondary = Color(0xFF475569), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9), onSecondaryContainer = Color(0xFF334155),
    tertiary = Color(0xFF0F766E), background = Color(0xFFF8FAFC), onBackground = Color(0xFF0F172A),
    surface = Color.White, onSurface = Color(0xFF0F172A), onSurfaceVariant = Color(0xFF64748B),
    surfaceVariant = Color(0xFFF1F5F9), surfaceContainer = Color.White,
    surfaceContainerLow = Color.White, surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFF1F5F9), surfaceContainerLowest = Color.White,
    outline = Color(0xFFCBD5E1), outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFB91C1C), onError = Color.White, errorContainer = Color(0xFFFEE2E2), onErrorContainer = Color(0xFF991B1B)
)
@Composable
fun SolarGridXMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SolarColors, typography = Typography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)), content = content)
}
