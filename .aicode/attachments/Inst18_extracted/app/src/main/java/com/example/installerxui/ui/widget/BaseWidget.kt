package com.example.installerxui.ui.widget

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
 * 设置项基类，遵循 Material 3 ListItem 规范。
 * 通过 [LocalSegmentedItemShape] 读取当前分段位置对应的圆角，实现卡片粘连。
 *
 * 图标支持两种来源：
 * - [icon]：ImageVector，供 Compose 侧直接传图标
 * - [iconRes]：drawable 资源 ID，供 Preference 组件等从 XML 属性读取的图标
 * 两者同时传入时优先使用 [iconRes]。
 *
 * 可通过 [containerColor] / [accentColor] 覆盖默认配色：容器背景用 [containerColor]，
 * 图标与描述用 [accentColor]（不传则沿用主题默认色）。
 */
@Composable
fun BaseWidget(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    onClick: (() -> Unit)? = null,
    containerColor: Color? = null,
    accentColor: Color? = null,
    trailingContent: @Composable () -> Unit = {},
) {
    val shape = LocalSegmentedItemShape.current
    val alpha = if (enabled) 1f else 0.38f

    val colors = ListItemDefaults.colors(
        containerColor = containerColor ?: MaterialTheme.colorScheme.surfaceBright,
        contentColor = MaterialTheme.colorScheme.onSurface,
        leadingContentColor = accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
        trailingContentColor = accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
        supportingContentColor = when {
            isError -> MaterialTheme.colorScheme.error
            accentColor != null -> accentColor
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )

    val headline: @Composable () -> Unit = {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
    }
    val supporting: (@Composable () -> Unit)? = description?.let { d ->
        { Text(text = d, style = MaterialTheme.typography.bodyMedium) }
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

    val itemModifier = modifier
        .fillMaxWidth()
        .clip(shape)

    if (onClick != null) {
        ListItem(
            modifier = itemModifier,
            content = headline,
            supportingContent = supporting,
            leadingContent = leading,
            trailingContent = trailingContent,
            verticalAlignment = Alignment.CenterVertically,
            colors = colors,
            enabled = enabled,
            onClick = onClick,
        )
    } else {
        ListItem(
            modifier = itemModifier.alpha(alpha),
            content = headline,
            supportingContent = supporting,
            leadingContent = leading,
            trailingContent = trailingContent,
            verticalAlignment = Alignment.CenterVertically,
            colors = colors,
        )
    }
}
