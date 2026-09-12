package com.brainquest.game.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

data class AppThemeDef(
    val id: String,
    val name: String,
    val price: Int,
    val seed: Color,
)

object AppThemes {
    val all = listOf(
        AppThemeDef("classic", "经典紫", 0, Color(0xFF6750A4)),
        AppThemeDef("ocean", "海洋蓝", 300, Color(0xFF00639B)),
        AppThemeDef("forest", "森林绿", 300, Color(0xFF2E6B34)),
        AppThemeDef("sunset", "落日橙", 500, Color(0xFF9A4600)),
        AppThemeDef("sakura", "樱花粉", 500, Color(0xFF984061)),
    )

    fun byId(id: String): AppThemeDef = all.find { it.id == id } ?: all.first()
}

private fun tint(seed: Color, over: Color, alpha: Float = 0.22f) =
    seed.copy(alpha = alpha).compositeOver(over)

fun themeScheme(def: AppThemeDef, dark: Boolean): ColorScheme {
    val seed = def.seed
    val surface = if (dark) Color(0xFF15131A) else Color(0xFFFFFBFE)
    val surfaceContainer = tint(seed, surface, if (dark) 0.28f else 0.10f)
    val container = tint(seed, if (dark) Color(0xFF1D1B20) else Color.White, 0.55f)
    return if (dark) darkColorScheme(
        primary = tint(seed, Color(0xFF4A4458), 0.75f).copy(alpha = 1f),
        onPrimary = Color.White,
        primaryContainer = tint(seed, Color(0xFF2B2635), 0.85f).copy(alpha = 1f),
        onPrimaryContainer = Color(0xFFEADDFF),
        secondary = seed,
        background = surface,
        onBackground = Color(0xFFE6E0E9),
        surface = surface,
        onSurface = Color(0xFFE6E0E9),
        surfaceVariant = surfaceContainer,
        onSurfaceVariant = Color(0xFFCAC4D0),
        surfaceContainerLow = tint(seed, surface, 0.16f),
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = tint(seed, surface, 0.40f),
    ) else lightColorScheme(
        primary = seed,
        onPrimary = Color.White,
        primaryContainer = container,
        onPrimaryContainer = seed,
        secondary = seed,
        background = surface,
        onBackground = Color(0xFF1D1B20),
        surface = surface,
        onSurface = Color(0xFF1D1B20),
        surfaceVariant = surfaceContainer,
        onSurfaceVariant = Color(0xFF49454F),
        surfaceContainerLow = tint(seed, surface, 0.05f),
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = tint(seed, surface, 0.16f),
    )
}
