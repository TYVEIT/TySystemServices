package com.android.ty.systemservices.compose.ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 还原 InstallerX 的 AnimatedFluidBackground：
 * 用多层 Canvas + infiniteRepeatable 颜色/透明度动画 + 随帧时间移动的径向渐变圆，
 * 模拟流动的液体/玻璃背景。由 enabled 控制是否启用（卡片开启模糊效果时作为底层）。
 */
@Composable
fun AnimatedFluidBackground(baseColor: Color, enabled: Boolean, modifier: Modifier = Modifier) {
    if (!enabled) return
    Box(modifier = modifier) {
        AnimatedFluidBackgroundLayers(baseColor)
    }
}

@Composable
private fun AnimatedFluidBackgroundLayers(baseColor: Color) {
    val transition = rememberInfiniteTransition(label = "fluid_background_transition")

    val primaryFlow by transition.animateColor(
        initialValue = baseColor.copy(alpha = 0.9f),
        targetValue = blend(baseColor, Color.Magenta, 0.6f, 0.2f),
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "primary_flow",
    )
    val secondaryFlow by transition.animateColor(
        initialValue = blend(baseColor, Color.Cyan, 0.7f, 0.25f),
        targetValue = blend(baseColor, Color.Blue, 0.85f, 0.15f),
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "secondary_flow",
    )
    val accentFlow by transition.animateColor(
        initialValue = blend(baseColor, Color.Yellow, 0.6f, 0.1f),
        targetValue = blend(baseColor, Color.Green, 0.8f, 0.18f),
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "accent_flow",
    )
    val complementFlow by transition.animateColor(
        initialValue = blend(baseColor, Color.Red, 0.5f, 0.12f),
        targetValue = blend(baseColor, Color(0xFFFF6B35), 0.75f, 0.2f),
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "complement_flow",
    )

    var timeState by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                timeState = (frameTimeNanos / 1_000_000_000.0).toFloat()
            }
        }
    }

    val time1 = timeState * 0.1f
    val time2 = timeState * 0.133f
    val time3 = timeState * 0.167f
    val microTime = timeState * 0.2f

    val layerAlpha1 by transition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "layer_alpha_1",
    )
    val layerAlpha2 by transition.animateFloat(
        initialValue = 1f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "layer_alpha_2",
    )
    val layerAlpha3 by transition.animateFloat(
        initialValue = 0.8f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(10000, easing = FastOutLinearInEasing), RepeatMode.Reverse),
        label = "layer_alpha_3",
    )

    // Layer 1: 三个主色流
    Canvas(Modifier.fillMaxSize().alpha(layerAlpha1)) {
        val w = size.width; val h = size.height
        val cx = w / 2; val cy = h / 2
        val maxR = maxOf(w, h)
        val centers = listOf(
            Offset(cx + w * 0.45f * sin(time1 * 0.8f + 0.5f) + w * 0.15f * cos(time2 * 0.7f),
                cy + h * 0.4f * cos(time1 * 0.9f) + h * 0.12f * sin(time2 * 1.1f + 1.2f)),
            Offset(cx + w * 0.5f * cos(time1 * 0.6f + 2.1f) + w * 0.18f * sin(time2 * 0.9f + 0.8f),
                cy + h * 0.42f * sin(time1 * 0.7f + 1.5f) + h * 0.15f * cos(time2 * 0.8f + 2f)),
            Offset(cx + w * 0.38f * sin(time1 * 0.75f + 3.8f) + w * 0.2f * cos(microTime * 1.2f),
                cy + h * 0.35f * cos(time1 * 0.85f + 2.7f) + h * 0.17f * sin(microTime * 1f + 1.1f)),
        )
        val radii = listOf(
            maxR * 0.8f + maxR * 0.12f * sin(microTime * 0.8f),
            maxR * 0.75f + maxR * 0.15f * cos(microTime * 0.9f + 1f),
            maxR * 0.85f + maxR * 0.1f * sin(microTime * 1.1f + 2.3f),
        )
        val colors = listOf(primaryFlow, secondaryFlow, accentFlow)
        for (i in centers.indices) {
            val c = colors[i]
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c, c.copy(alpha = c.alpha * 0.6f), c.copy(alpha = 0f)),
                    center = centers[i], radius = radii[i],
                ),
                center = centers[i], radius = radii[i],
            )
        }
    }

    // Layer 2: 五个更细的流
    Canvas(Modifier.fillMaxSize().alpha(layerAlpha2)) {
        val w = size.width; val h = size.height
        val cx = w / 2; val cy = h / 2
        val maxR = maxOf(w, h)
        val centers = (0..4).map { i ->
            val phase = i * PI.toFloat() / 2.5f
            Offset(
                cx + w * 0.35f * sin(time2 * 0.6f + phase) + w * 0.2f * cos(time3 * 0.5f + phase * 1.5f) + w * 0.08f * sin(microTime * 1.5f + phase * 0.8f),
                cy + h * 0.32f * cos(time2 * 0.7f + phase * 1.2f) + h * 0.25f * sin(time3 * 0.6f + phase * 0.7f) + h * 0.1f * cos(microTime * 1.3f + phase * 1.3f),
            )
        }
        val radii = (0..4).map { i ->
            val base = maxR * (0.55f + 0.15f * sin(i.toFloat()))
            base + maxR * 0.12f * cos(microTime * (0.8f + i * 0.2f))
        }
        val cols = listOf(
            secondaryFlow.copy(alpha = secondaryFlow.alpha * 0.8f),
            accentFlow.copy(alpha = accentFlow.alpha * 0.7f),
            complementFlow.copy(alpha = complementFlow.alpha * 0.9f),
            primaryFlow.copy(alpha = primaryFlow.alpha * 0.6f),
            secondaryFlow.copy(alpha = secondaryFlow.alpha * 0.75f),
        )
        for (i in centers.indices) {
            val c = cols[i]
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c, c.copy(alpha = c.alpha * 0.4f), c.copy(alpha = c.alpha * 0.1f), c.copy(alpha = 0f)),
                    center = centers[i], radius = radii[i],
                ),
                center = centers[i], radius = radii[i],
            )
        }
    }

    // Layer 3: 纹理点
    Canvas(Modifier.fillMaxSize().alpha(layerAlpha3)) {
        val w = size.width; val h = size.height
        val cx = w / 2; val cy = h / 2
        val maxR = maxOf(w, h)
        val fastTime = microTime * 2.5f
        val mediumTime = time1 * 1.5f
        val points = (0..7).map { i ->
            val angle = i * 2 * PI.toFloat() / 8
            val dynR = w * 0.3f + w * 0.2f * sin(fastTime + angle * 1.5f)
            val turb = w * 0.06f * cos(fastTime * 0.8f + angle * 2f)
            Offset(
                cx + dynR * cos(angle + mediumTime * 0.3f) + turb,
                cy + dynR * sin(angle + mediumTime * 0.3f) + h * 0.05f * sin(fastTime * 1.2f + angle),
            )
        }
        for (i in points.indices) {
            val dynR = maxR * (0.2f + 0.1f * sin(fastTime + i * 0.5f))
            val opacity = 0.25f + 0.15f * cos(fastTime * 0.7f + i * 0.3f)
            val c = when (i % 4) {
                0 -> primaryFlow; 1 -> secondaryFlow; 2 -> accentFlow; else -> complementFlow
            }.copy(alpha = opacity)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c, c.copy(alpha = 0f)),
                    center = points[i], radius = dynR,
                ),
                center = points[i], radius = dynR,
            )
        }
    }

    // Layer 4: 中心辉光
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val maxR = maxOf(w, h)
        val glowTime = time1 * 0.5f
        val center = Offset(w / 2, h / 2)
        val radius = maxR * (0.75f + 0.15f * sin(glowTime * 0.6f))
        val intensity = 0.08f + 0.04f * cos(glowTime * 0.8f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    baseColor.copy(alpha = intensity),
                    baseColor.copy(alpha = intensity * 0.5f),
                    Color.Transparent,
                ),
                center = center, radius = radius,
            ),
            center = center, radius = radius,
        )
    }
}

/**
 * 安全颜色混合：将 base 以 baseAlpha、tint 以 tintAlpha 在 sRGB 空间线性混合，
 * 替代 Color.compositeOver，避免颜色空间不一致导致的闪退。
 */
private fun blend(base: Color, tint: Color, baseAlpha: Float, tintAlpha: Float): Color {
    val b = base.convert(ColorSpaces.Srgb)
    val t = tint.convert(ColorSpaces.Srgb)
    val total = baseAlpha + tintAlpha
    if (total <= 0f) return Color(0f, 0f, 0f, 0f, ColorSpaces.Srgb)
    val r = (b.red * baseAlpha + t.red * tintAlpha) / total
    val g = (b.green * baseAlpha + t.green * tintAlpha) / total
    val bl = (b.blue * baseAlpha + t.blue * tintAlpha) / total
    return Color(
        r.coerceIn(0f, 1f),
        g.coerceIn(0f, 1f),
        bl.coerceIn(0f, 1f),
        total.coerceIn(0f, 1f),
        ColorSpaces.Srgb,
    )
}
