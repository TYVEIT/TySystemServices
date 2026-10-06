package com.android.ty.systemservices.compose.ui

import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * 还原 InstallerX 的 SwitchWidget：
 * 左侧图标+标题+描述，右侧 Switch（带 Check/Close thumb 图标）。
 * 点击整行或 Switch 时触发触觉反馈，使用 Material3 ripple 波纹按压效果。
 *
 * 通过 [shape] 实现"卡片粘连"：同一分组内首卡只圆顶部、中卡直角、末卡只圆底部，
 * 且去除垂直间距，使多张卡片视觉上合并为一个整体块。
 */
@Composable
fun SwitchRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    @androidx.annotation.DrawableRes iconRes: Int? = null,
    shape: Shape = MaterialTheme.shapes.large,
) {
    val haptic = LocalHapticFeedback.current

    val handle: (Boolean) -> Unit = { v ->
        haptic.performHapticFeedback(if (v) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        onCheckedChange(v)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { handle(!checked) },
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (iconRes != null) {
                Icon(painter = painterResource(iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            } else if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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

/** 粘连卡片分组中首卡的形状：仅顶部圆角 */
val StickyShapeFirst = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp)

/** 粘连卡片分组中中间卡片的形状：无圆角 */
val StickyShapeMiddle = RoundedCornerShape(0.dp)

/** 粘连卡片分组中末卡的形状：仅底部圆角 */
val StickyShapeLast = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 28.dp, bottomEnd = 28.dp)

/** 独立卡片的形状：四角全圆角 */
val StickyShapeSingle = RoundedCornerShape(28.dp)

/**
 * 卡片在粘连分组中的位置，与 attrs.xml 中 cardPosition 枚举值一一对应。
 * AUTO(-1) 表示自动检测：根据 Preference 在父分组中的位置和相邻的 PreferenceCategory 边界推断。
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

/**
 * 根据卡片位置返回对应的粘连形状。
 */
fun CardPosition.toShape(): androidx.compose.ui.graphics.Shape = when (this) {
    CardPosition.AUTO -> StickyShapeSingle
    CardPosition.FIRST -> StickyShapeFirst
    CardPosition.MIDDLE -> StickyShapeMiddle
    CardPosition.LAST -> StickyShapeLast
    CardPosition.SINGLE -> StickyShapeSingle
}
