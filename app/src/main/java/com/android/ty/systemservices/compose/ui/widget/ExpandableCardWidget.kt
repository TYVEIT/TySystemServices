// SPDX-License-Identifier: GPL-3.0-only
package com.android.ty.systemservices.compose.ui.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.ty.systemservices.R

/**
 * 动态可展开设置卡片：头部（可选图标 + 标题 + 描述 + 展开/收起按钮）与展开内容均参数化，
 * 无任何硬编码文案/图标；配色取自 [MaterialTheme] 动态取色，随主题自动变化。
 *
 * 图标支持两种来源，[iconRes] 优先：
 * - [icon]：ImageVector，供 Compose 侧直接传图标
 * - [iconRes]：drawable 资源 ID，供 Preference 组件从 XML 属性读取
 *
 * @param expanded 是否处于展开状态
 * @param onExpandedChange 展开状态变化回调
 * @param title 头部主标题
 * @param description 可选的头部副标题
 * @param icon 可选的头部前导图标（ImageVector）
 * @param iconRes 可选的头部前导图标（drawable 资源 ID）
 * @param shape 卡片形状，默认 M3 卡片圆角；Preference 侧可传入分段粘连形状
 * @param containerColor 卡片背景色，默认 surfaceBright
 * @param modifier 外部修饰符，默认保留顶部 8dp 间距（与原视觉一致）
 * @param content 展开后显示的内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandableCard(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    shape: Shape = CardDefaults.shape,
    containerColor: Color? = null,
    modifier: Modifier = Modifier.padding(top = 8.dp),
    content: @Composable () -> Unit = {},
) {
    Card(
        onClick = { onExpandedChange(!expanded) },
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor ?: MaterialTheme.colorScheme.surfaceBright,
        ),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (iconRes != null) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    } else if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (description != null) {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                IconButton(onClick = { onExpandedChange(!expanded) }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                content()
            }
        }
    }
}
