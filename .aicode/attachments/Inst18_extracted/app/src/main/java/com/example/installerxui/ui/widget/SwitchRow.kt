package com.example.installerxui.ui.widget

import com.example.installerxui.ui.util.animateShapeAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/** 粘连卡片分组中首卡的形状：仅顶部圆角 */
val StickyShapeFirst = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** 粘连卡片分组中中间卡片的形状：无圆角 */
val StickyShapeMiddle = RoundedCornerShape(0.dp)

/** 粘连卡片分组中末卡的形状：仅底部圆角 */
val StickyShapeLast = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)

/** 独立卡片的形状：四角全圆角 */
val StickyShapeSingle = RoundedCornerShape(28.dp)

/**
 * 卡片在粘连分组中的位置，与 attrs.xml 中 cardPosition 枚举值一一对应。
 * AUTO(-1) 表示自动检测。
 */
enum class CardPosition(val value: Int) {
    AUTO(-1),
    FIRST(0),
    MIDDLE(1),
    LAST(2),
    SINGLE(3);

    companion object {
        fun fromValue(value: Int): CardPosition = entries.firstOrNull { it.value == value } ?: AUTO
    }
}

/** 根据卡片位置返回对应的粘连形状。 */
fun CardPosition.toShape(): Shape = when (this) {
    CardPosition.AUTO -> StickyShapeSingle
    CardPosition.FIRST -> StickyShapeFirst
    CardPosition.MIDDLE -> StickyShapeMiddle
    CardPosition.LAST -> StickyShapeLast
    CardPosition.SINGLE -> StickyShapeSingle
}

/**
 * 还原 InstallerX 的开关：
 * 左侧图标 + 标题 + 描述，右侧 Switch（带 Check/Close thumb 图标）。
 * 按压动画包含：
 *  - A. Ripple 水波纹（clickable 的 indication）
 *  - B. 圆角形状变形（按压时从分段圆角动画到完整圆角）
 * 开关切换带 ToggleOn/ToggleOff 触觉反馈。
 */
@Composable
fun SwitchRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    shape: Shape = StickyShapeSingle,
) {
    val haptic = LocalHapticFeedback.current

    val handle: (Boolean) -> Unit = { v ->
        haptic.performHapticFeedback(if (v) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        onCheckedChange(v)
    }

    // B. 圆角变形：按压时从分段圆角动画到完整圆角，松开回弹
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val animatedShape by animateShapeAsState(
        targetValue = if (pressed) StickyShapeSingle else shape,
        label = "switchShape",
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
        shape = animatedShape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current, // A. Ripple
                    onClick = { handle(!checked) },
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = handle,
                colors = SwitchDefaults.colors(
                    checkedIconColor = MaterialTheme.colorScheme.primary,
                    uncheckedIconColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                thumbContent = {
                    Icon(
                        imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        }
    }
}
