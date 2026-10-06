package com.android.ty.systemservices.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 还原 InstallerX 的 StatusCard：
 * - ElevatedCard + 容器色（不同级别用 primary/secondary/tertiaryContainer）
 * - useBlur 为 true 时：半透明容器 + 底层 [AnimatedFluidBackground] 流体背景 + blur 模糊，
 *   形成玻璃态卡片；并禁用 elevation 避免阴影穿帮。
 * - 内部展示图标、标题、版本信息与更新提示。
 */
@Composable
fun StatusCard(
    title: String = "InstallerX",
    subtitle: String = "Revived Edition · v1.0.0 (Stable)",
    hasUpdate: Boolean = true,
    remoteVersion: String = "1.2.0",
    useBlur: Boolean = true,
    onClick: () -> Unit = {},
) {
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val onContainerColor = MaterialTheme.colorScheme.onPrimaryContainer

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (useBlur) containerColor.copy(alpha = 0.18f) else containerColor,
            contentColor = onContainerColor,
        ),
        elevation = if (useBlur) CardDefaults.elevatedCardElevation(
            defaultElevation = 0.dp, pressedElevation = 0.dp,
            focusedElevation = 0.dp, hoveredElevation = 0.dp, draggedElevation = 0.dp,
        ) else CardDefaults.elevatedCardElevation(),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 玻璃态：底层铺流体背景
            AnimatedFluidBackground(
                baseColor = containerColor,
                enabled = useBlur,
                modifier = Modifier.matchParentSize(),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(vertical = 28.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(containerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = onContainerColor,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
                if (hasUpdate) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "发现新版本 $remoteVersion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/**
 * 玻璃态演示卡片：展示 blur + 半透明 + 流体背景叠加效果。
 */
@Composable
fun GlassDemoCard(useBlur: Boolean = true) {
    val color = MaterialTheme.colorScheme.secondaryContainer
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (useBlur) color.copy(alpha = 0.2f) else color,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (useBlur) 0.dp else 2.dp),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AnimatedFluidBackground(baseColor = color, enabled = useBlur, modifier = Modifier.matchParentSize())
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("液态玻璃效果", style = MaterialTheme.typography.titleMedium)
                Text(
                    "半透明容器 + 多层流体径向渐变 + 模糊，呈现玻璃质感。API 33+ 上还可叠加 RuntimeShader 折射。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
                }
            }
        }
    }
}
