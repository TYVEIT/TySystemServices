package com.android.ty.systemservices.compose.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.launch

/**
 * 还原 InstallerX 的 InteractiveHighlight：
 * 按下时在按下位置绘制径向白色高光（用 AGSL RuntimeShader，API 33+），
 * 同时配合按压缩放（spring 弹性）。
 *
 * Android 12 以下兼容：RuntimeShader 需 API 33 (Tiramisu)，
 * 低于此版本降级为 drawRect 半透明白色叠加。
 */
@Suppress("DEPRECATION")
fun Modifier.interactiveHighlight(
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    onLongPress: () -> Unit = {},
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val pressProgress = remember { Animatable(0f, 0.001f) }
    val longPressProgress = remember { Animatable(0f, 0.001f) }
    val position = remember { Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold) }
    val scale = remember { Animatable(1f, 0.001f) }
    // 标记本次按压是否已触发长按，用于屏蔽后续的 onClick
    // 用 MutableState 保证值在重组后依然对 pointerInput 内的 lambda 可见
    var longPressed by remember { mutableStateOf(false) }

    val pressSpec = spring<Float>(0.5f, 300f, 0.001f)
    val posSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)
    val scaleSpec = spring<Float>(0.6f, 250f, 0.001f)
    val longPressSpec = spring<Float>(0.5f, 300f, 0.001f)

    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(
                """
                uniform float2 size;
                layout(color) uniform half4 color;
                uniform float radius;
                uniform float2 position;
                half4 main(float2 coord) {
                    float dist = distance(coord, position);
                    float intensity = smoothstep(radius, radius * 0.5, dist);
                    return color * intensity;
                }
                """.trimIndent()
            )
        } else null
    }

    val modifier: Modifier = Modifier
        .scale(scale.value)
        .drawWithContent {
            val progress = pressProgress.value
            val lpProgress = longPressProgress.value
            if (progress > 0f) {
                // 所有版本可用：整体半透明叠加
                drawRect(Color.White.copy(0.06f * progress), blendMode = BlendMode.Plus)
                if (shader != null) {
                    shader.apply {
                        val pos = position.value
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("color", Color.White.copy(0.12f * progress).toArgb())
                        setFloatUniform("radius", size.minDimension * 1.2f)
                        setFloatUniform(
                            "position",
                            pos.x.fastCoerceIn(0f, size.width),
                            pos.y.fastCoerceIn(0f, size.height),
                        )
                    }
                    drawRect(ShaderBrush(shader), blendMode = BlendMode.Plus)
                }
            }
            // 长按时的整块高亮（对应截图3：卡片整体变浅）
            if (lpProgress > 0f) {
                drawRect(Color.White.copy(0.12f * lpProgress), blendMode = BlendMode.Plus)
            }
            drawContent()
        }
        .pointerInput(enabled) {
            detectTapGestures(
                onPress = { offset ->
                    longPressed = false
                    scope.launch {
                        launch { pressProgress.animateTo(1f, pressSpec) }
                        launch { position.snapTo(offset) }
                        launch { scale.animateTo(0.96f, scaleSpec) }
                    }
                    val released = tryAwaitRelease()
                    scope.launch {
                        launch { pressProgress.animateTo(0f, pressSpec) }
                        launch { longPressProgress.animateTo(0f, longPressSpec) }
                        launch { scale.animateTo(1f, scaleSpec) }
                    }
                    // 长按已消费本次按压，不再触发普通点击
                    if (released && enabled && !longPressed) onClick()
                },
                onLongPress = {
                    longPressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        longPressProgress.animateTo(1f, longPressSpec)
                        // 长按弹性：略微多压一点再回弹
                        scale.animateTo(0.92f, scaleSpec)
                    }
                    onLongPress()
                },
            )
        }

    modifier
}

/**
 * 更简单的弹性按压缩放：仅缩放，不绘制高光。
 * 用 spring 实现"按下回弹"的弹性手感。
 */
fun Modifier.springPressScale(
    pressedScale: Float = 0.95f,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleAnim = remember { Animatable(1f, 0.001f) }
    LaunchedEffect(isPressed) {
        scaleAnim.animateTo(if (isPressed) pressedScale else 1f, spring(0.6f, 250f, 0.001f))
    }
    Modifier
        .scale(scaleAnim.value)
        .combinedClickable(interactionSource = interactionSource, indication = null) {}
}
