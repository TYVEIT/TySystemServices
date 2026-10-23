package com.example.installerxui.ui.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.installerxui.ui.animation.AnimatedFluidBackground

/**
 * 状态卡：展示应用图标、名称与版本信息。
 * [useBlur] 开启后卡片半透明、去掉阴影，底层叠加流体动画背景，实现毛玻璃效果。
 */
@Composable
fun StatusCard(
    appName: String,
    version: String,
    modifier: Modifier = Modifier,
    useBlur: Boolean = false,
) {
    val containerColor = MaterialTheme.colorScheme.primaryContainer

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (useBlur) containerColor.copy(alpha = 0.15f) else containerColor,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        elevation = if (useBlur) {
            CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
        } else {
            CardDefaults.elevatedCardElevation()
        },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AnimatedFluidBackground(
                baseColor = containerColor,
                enabled = useBlur,
                modifier = Modifier.matchParentSize(),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Android,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                )
                Text(text = appName, style = MaterialTheme.typography.titleMedium)
                Text(text = version, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
