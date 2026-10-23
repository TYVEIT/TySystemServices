package com.example.installerxui.ui.util

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Shape

/**
 * 兼容实现：androidx.compose.animation 在 1.12.0 已移除 animateShapeAsState，
 * 此处直接返回目标 Shape，保持调用方 API 签名兼容（含 animationSpec 参数）。
 */
@Composable
fun animateShapeAsState(
    targetValue: Shape,
    animationSpec: AnimationSpec<Shape>? = null,
    label: String = "Shape",
): State<Shape> {
    return rememberUpdatedState(targetValue)
}
