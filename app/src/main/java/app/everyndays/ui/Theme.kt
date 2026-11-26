package app.everyndays.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6B5C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4F1ED),
    onPrimaryContainer = Color(0xFF0A4F44),
    secondaryContainer = Color(0xFFDCEFEA),
    onSecondaryContainer = Color(0xFF0A4F44),
    background = Color(0xFFFBFBFA),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFBFBFA),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFF0F0EC),
    onSurfaceVariant = Color(0xFF5E6066),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F6F3),
    surfaceContainer = Color(0xFFF1F1EE),
    surfaceContainerHigh = Color(0xFFEDEDEA),
    surfaceContainerHighest = Color(0xFFE7E7E3),
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF2F2EF),
    inversePrimary = Color(0xFF7FD3C0),
    outline = Color(0xFFC9C9C4),
    outlineVariant = Color(0xFFE6E6E3),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD3C0),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF1C3631),
    onPrimaryContainer = Color(0xFFBFEDE2),
    secondaryContainer = Color(0xFF24403A),
    onSecondaryContainer = Color(0xFFBFEDE2),
    background = Color(0xFF121414),
    onBackground = Color(0xFFE3E3E0),
    surface = Color(0xFF121414),
    onSurface = Color(0xFFE3E3E0),
    surfaceVariant = Color(0xFF2A2C2C),
    onSurfaceVariant = Color(0xFFA9ACB0),
    surfaceContainerLowest = Color(0xFF0D0F0F),
    surfaceContainerLow = Color(0xFF1A1C1C),
    surfaceContainer = Color(0xFF1E2020),
    surfaceContainerHigh = Color(0xFF282A2A),
    surfaceContainerHighest = Color(0xFF333535),
    inverseSurface = Color(0xFFE3E3E0),
    inverseOnSurface = Color(0xFF2F3033),
    inversePrimary = Color(0xFF0F6B5C),
    outline = Color(0xFF5A5D60),
    outlineVariant = Color(0xFF2E3132),
    error = Color(0xFFFFB4A8),
    onError = Color(0xFF690005),
)

/** The colors a reminder can have. Index is stored with the reminder. */
object Palette {
    val names = listOf("Teal", "Blue", "Purple", "Pink", "Orange", "Yellow", "Grey")
    private val light = listOf(
        Color(0xFF0F6B5C), Color(0xFF3B5BDB), Color(0xFF7C5CC4), Color(0xFFC2477A),
        Color(0xFFC2620C), Color(0xFFC99A06), Color(0xFF66717C),
    )
    private val dark = listOf(
        Color(0xFF6FCDB8), Color(0xFF91A7FF), Color(0xFFC0A8F0), Color(0xFFF29BBE),
        Color(0xFFFFB07A), Color(0xFFF0CE5C), Color(0xFFAEB7C0),
    )
    val size: Int get() = light.size

    @Composable
    fun color(index: Int): Color {
        val list = if (isSystemInDarkTheme()) dark else light
        return list[index.coerceIn(0, list.lastIndex)]
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
