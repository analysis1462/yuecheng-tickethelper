package com.yuecheng.ticket.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Blue = Color(0xFF0B57D0)
val BlueDark = Color(0xFF083B8F)
val Orange = Color(0xFFFC5800)
val Green = Color(0xFF1E8E3E)
val PageBg = Color(0xFFF4F6FA)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = BlueDark,
    secondary = Orange,
    onSecondary = Color.White,
    tertiary = Green,
    background = PageBg,
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE8EDF5),
    onSurfaceVariant = Color(0xFF5B6470),
    error = Color(0xFFD93025),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF1B4B8F),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFFFB68F),
    onSecondary = Color(0xFF4A1500),
    secondaryContainer = Color(0xFF6E3A1F),
    onSecondaryContainer = Color(0xFFFFDBCC),
    tertiary = Color(0xFF9CD67C),
    onTertiary = Color(0xFF0E3900),
    tertiaryContainer = Color(0xFF245A10),
    onTertiaryContainer = Color(0xFFC8F1A8),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF1B1E24),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF2A2E37),
    onSurfaceVariant = Color(0xFFA9B0BC),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF410E0B),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
)

/** dark 为 null 时跟随系统;传入 true/false 强制夜间/日间。切换时颜色平滑过渡 */
@Composable
fun YcTheme(dark: Boolean? = null, content: @Composable () -> Unit) {
    val useDark = dark ?: isSystemInDarkTheme()
    val target = if (useDark) DarkColors else LightColors

    @Composable
    fun animated(color: Color): Color =
        animateColorAsState(color, tween(450), label = "themeColor").value

    val scheme = target.copy(
        primary = animated(target.primary),
        onPrimary = animated(target.onPrimary),
        primaryContainer = animated(target.primaryContainer),
        onPrimaryContainer = animated(target.onPrimaryContainer),
        secondary = animated(target.secondary),
        onSecondary = animated(target.onSecondary),
        tertiary = animated(target.tertiary),
        background = animated(target.background),
        onBackground = animated(target.onBackground),
        surface = animated(target.surface),
        onSurface = animated(target.onSurface),
        surfaceVariant = animated(target.surfaceVariant),
        onSurfaceVariant = animated(target.onSurfaceVariant),
        error = animated(target.error),
    )

    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * 窗口背景跟随主题底色。主题 XML 恒为 Light,窗口底默认近白:
 * Compose 页面转场(滑动+淡入淡出)和 Activity 打开/关闭动画期间,
 * 界面半透明会透出窗口背景,夜间模式下白光晃眼。各 Activity 在 onCreate 里调用。
 */
fun Activity.applyWindowBackground(useDark: Boolean) {
    window.setBackgroundDrawable(
        android.graphics.drawable.ColorDrawable(
            android.graphics.Color.parseColor(if (useDark) "#111318" else "#F4F6FA"),
        )
    )
}
