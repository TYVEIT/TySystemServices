package com.example.installerxui.ui.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 提示卡基础组件：展示提示或须知信息，背景色取自 tertiary。
 *
 * @param modifier 应用到卡片的 Modifier。
 * @param cardShape 卡片形状。
 * @param noPadding 不额外添加外边距。
 * @param tipContent 主提示内容（通常是 Row(Icon + Text)）。
 * @param actionContent 操作区（可选按钮/底部留白），在 ColumnScope 内，可用 align 等。
 */
@Composable
private fun TipCard(
    modifier: Modifier = Modifier,
    cardShape: RoundedCornerShape = RoundedCornerShape(16.dp),
    noPadding: Boolean = false,
    tipContent: @Composable () -> Unit,
    actionContent: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = if (noPadding) {
            modifier
                .fillMaxWidth()
        } else {
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
        ),
        shape = cardShape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp),
        ) {
            tipContent()
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = actionContent,
            )
        }
    }
}

/**
 * 通用提示卡：图标 + 单行文本。
 */
@Composable
fun InfoTipCard(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Filled.Info,
    noPadding: Boolean = false,
) {
    TipCard(
        modifier = modifier,
        noPadding = noPadding,
        tipContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiary,
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiary,
                )
            }
        },
    ) {
        Spacer(modifier = Modifier.size(16.dp))
    }
}

/**
 * 标题提示卡：标题 + 正文。
 */
@Composable
fun TitleTipCard(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Filled.Info,
    noPadding: Boolean = false,
) {
    TipCard(
        modifier = modifier,
        noPadding = noPadding,
        tipContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    icon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiary,
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiary,
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiary,
                )
            }
        },
    ) {
        Spacer(modifier = Modifier.size(16.dp))
    }
}
