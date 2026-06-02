package tech.ittools.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFF18A058)
private val BrandDark = Color(0xFF0F7C42)
private val Surface = Color(0xFF1E1E2A)
private val SurfaceVariant = Color(0xFF24242F)
private val OnSurface = Color(0xFFE6E6EC)

private val ItToolsColors = darkColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = BrandDark,
    onPrimaryContainer = Color.White,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurface,
)

/** The native shell stays dark to match the splash and it-tools' default theme. */
@Composable
fun ITToolsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ItToolsColors, content = content)
}
