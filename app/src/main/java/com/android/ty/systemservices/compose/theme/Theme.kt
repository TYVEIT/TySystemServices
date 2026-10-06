package com.android.ty.systemservices.compose.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.core.view.WindowCompat

private val LocalIsDark = staticCompositionLocalOf { false }
private val LocalSeedColor = staticCompositionLocalOf { Color.Unspecified }
private val LocalBackgroundColor = staticCompositionLocalOf { Color.Unspecified }

val LocalInstallerColorScheme = staticCompositionLocalOf<ColorScheme> { error("No ColorScheme") }

object InstallerTheme {
    val colorScheme: ColorScheme
        @Composable @ReadOnlyComposable
        get() = LocalInstallerColorScheme.current

    val isDark: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalIsDark.current

    val seedColor: Color
        @Composable @ReadOnlyComposable
        get() = LocalSeedColor.current

    val backgroundColor: Color
        @Composable @ReadOnlyComposable
        get() = LocalBackgroundColor.current
}

/**
 * 主题入口。
 *
 * Android 12 以下兼容策略（与 InstallerX 一致）：
 * 1. 系统动态取色 `android.R.color.system_accent1_500` 只有 Android 12 (S, API 31) 才有；
 *    低于 12 时，InstallerX 用 MonetCompat 在 Application 启动时回退（从壁纸提取颜色），
 *    再用 material-kolor 生成完整 ColorScheme。这里演示：useDynamicColor 为 true 且 SDK>=S
 *    时取系统色，否则使用用户选择的种子色 seedColor。
 * 2. material-kolor 本身不依赖系统，能在任何 API 上基于任意种子色生成 Material You 配色，
 *    这是 Android 12 以下能有 Material You 风格的关键。
 * 3. AGSL RuntimeShader（流体/玻璃特效）需 API 33+，在组件内部用 SDK 判断做降级。
 */
@Composable
fun InstallerTheme(
    themeMode: ThemeMode,
    paletteStyle: PaletteStyle,
    colorSpec: ThemeColorSpec,
    useDynamicColor: Boolean,
    seedColor: Color,
    backgroundColor: Color = Color.Unspecified,
    useDynamicBackgroundColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // 动态取色：仅 Android 12+ 可用系统强调色；其余回退到种子色（MonetCompat 已在 App 启动时准备）
    val keyColor = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        colorResource(id = android.R.color.system_accent1_500)
    } else {
        seedColor
    }

    val baseScheme = androidx.compose.runtime.remember(keyColor, isDark, paletteStyle, colorSpec) {
        dynamicColorScheme(keyColor, isDark, paletteStyle, colorSpec)
    }

    // 颜色切换时的弹性过渡
    val animatedScheme = baseScheme.animateAsState()

    // 计算背景色：动态取色时从种子色派生柔和背景，否则使用用户选择的背景色
    // 使用手动线性插值混合，避免 compositeOver 的颜色空间问题导致闪退
    val effectiveBgColor = if (useDynamicBackgroundColor) {
        val base = if (isDark) Color(0xFF121212) else Color(0xFFFAFAFA)
        val alpha = if (isDark) 0.12f else 0.08f
        blendColors(seedColor, base, alpha)
    } else {
        // 确保不会把 Color.Unspecified 传给 background()，否则会闪退
        if (backgroundColor == Color.Unspecified) Color(0xFFF5F0FA) else backgroundColor
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // 安全获取 Activity：ComposeView 的 context 可能是 ContextThemeWrapper
            val activity = findActivity(view.context)
            if (activity != null) {
                val window = activity.window
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                window.statusBarColor = android.graphics.Color.TRANSPARENT
            }
        }
    }

    // 将当前配色同步到全局持有器，供 Preference 内的 Compose 内容复用同一主题
    SideEffect {
        ThemeStateHolder.colorScheme.value = animatedScheme
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalIsDark provides isDark,
        LocalSeedColor provides seedColor,
        LocalBackgroundColor provides effectiveBgColor,
        LocalInstallerColorScheme provides animatedScheme,
    ) {
        MaterialExpressiveTheme(
            colorScheme = animatedScheme,
            motionScheme = MotionScheme.expressive(),
            typography = Typography(),
            content = content,
        )
    }
}

/**
 * 手动线性插值混合两个颜色，foreground 以 alpha 透明度叠加在 background 上。
 * 避免使用 Color.compositeOver，后者在部分 Compose 版本/颜色空间组合下可能闪退。
 */
private fun blendColors(foreground: Color, background: Color, alpha: Float): Color {
    val f = foreground.convert(ColorSpaces.Srgb)
    val b = background.convert(ColorSpaces.Srgb)
    val r = f.red * alpha + b.red * (1f - alpha)
    val g = f.green * alpha + b.green * (1f - alpha)
    val bl = f.blue * alpha + b.blue * (1f - alpha)
    return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), bl.coerceIn(0f, 1f), 1f, ColorSpaces.Srgb)
}

/**
 * 从 Context 链中安全查找 Activity，处理 ContextThemeWrapper 等包装场景。
 */
private tailrec fun findActivity(context: Context): Activity? {
    return when (context) {
        is Activity -> context
        is ContextWrapper -> findActivity(context.baseContext)
        else -> null
    }
}
