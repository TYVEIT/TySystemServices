// SPDX-License-Identifier: GPL-3.0-only
package com.android.ty.systemservices.compose.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * [BaseWidget] 的滑杆版本：**专供 SeekBar 使用**。
 *
 * 与 [BaseWidget] 的差异只有一处——底部多了一条 [Slider]，其余完全对齐：
 * - 头部仍是 M3 `ListItem`（图标 / 标题 / 描述 / 右侧数值），左右 16dp、上下 12dp，
 *   与同组其它条目同一套度量，卡片高度只多出滑杆本身；
 * - 形状取 [LocalSegmentedItemShape]，放进 `SegmentedColumn` 后会自动与相邻条目
 *   "粘连"：首卡只圆上角、中卡直角、末卡只圆下角、单卡四角全圆；
 * - 头部用 `ListItem(onClick = ...)` 触发，点按时有 M3 水波纹（ripple），
 *   且被 `clip(shape)` 限制在卡片圆角内；
 * - 背景色默认 `surfaceBright`，与其它条目同底色；外部传 [containerColor] 可覆盖。
 *
 * @param title 标题
 * @param progress 当前进度
 * @param onProgressChange 拖动回调
 * @param valueText 右侧显示的当前值文本
 * @param modifier 外部修饰符
 * @param description 可选副标题
 * @param valueRange 取值范围
 * @param steps 档位数（0 表示连续）
 * @param icon 可选前导图标（ImageVector）
 * @param iconRes 可选前导图标（drawable 资源 ID），优先于 [icon]
 * @param enabled 是否可用
 * @param containerColor 卡片底色，默认 surfaceBright
 */
@Composable
fun BaseWidget_Seek(
    title: String,
    progress: Float,
    onProgressChange: (Float) -> Unit,
    valueText: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    enabled: Boolean = true,
    containerColor: Color? = null,
) {
    val shape = LocalSegmentedItemShape.current

    val colors = ListItemDefaults.colors(
        // 底色交给外层 Column，ListItem 保持透明，避免圆角处出现双层面
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        leadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        trailingContentColor = MaterialTheme.colorScheme.primary,
        supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val supporting: (@Composable () -> Unit)? = description?.let { text ->
        { Text(text = text, style = MaterialTheme.typography.bodyMedium) }
    }

    val leading: (@Composable () -> Unit)? = when {
        iconRes != null -> {
            {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        icon != null -> {
            { Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp)) }
        }

        else -> null
    }


    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor ?: MaterialTheme.colorScheme.surfaceBright),
    ) {
        ListItem(
            onClick = {},  // 只为水波纹：点击本身无业务动作，真正的交互在下方 Slider
            leadingContent = leading,
            supportingContent = supporting,
            trailingContent = {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.alpha(if (enabled) 1f else 0.38f),
                )
            },
            colors = colors,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.alpha(if (enabled) 1f else 0.38f),
            )
        }
        Slider(
            value = progress,
            onValueChange = onProgressChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp)
                .padding(bottom = 12.dp),
        )
    }
}
