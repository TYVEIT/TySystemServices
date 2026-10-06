package com.android.ty.systemservices.compose.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class HomeState(
    val useBlur: Boolean = true,
    val notification: Boolean = false,
    val secureMode: Boolean = true,
    val showTip: Boolean = true,
)

/**
 * 首页：集中展示卡片、开关、提示卡、弹性动画。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomePage(
    state: HomeState,
    onStateChange: (HomeState) -> Unit,
    onOpenTheme: () -> Unit,
    onOpenInstaller: () -> Unit,
    onOpenPreference: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TopAppBar(title = { Text("InstallerX UI Demo") })

        Spacer(Modifier.height(8.dp))

        StatusCard(useBlur = state.useBlur, onClick = onOpenInstaller)

        Spacer(Modifier.height(12.dp))

        AnimatedVisibility(
            visible = state.showTip,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            TipCard(
                text = "这是一个还原 InstallerX 视觉与动画的演示。点击右上角进入主题设置。",
                actionText = "知道了",
                onAction = { onStateChange(state.copy(showTip = false)) },
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "视觉开关",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        SwitchRow(
            title = "液态玻璃背景",
            description = "卡片使用流体动画 + 半透明模糊",
            icon = Icons.Outlined.Info,
            checked = state.useBlur,
            onCheckedChange = { onStateChange(state.copy(useBlur = it)) },
            shape = StickyShapeFirst,
        )
        SwitchRow(
            title = "通知安装",
            description = "通过通知栏完成安装流程",
            icon = Icons.Outlined.Notifications,
            checked = state.notification,
            onCheckedChange = { onStateChange(state.copy(notification = it)) },
            shape = StickyShapeMiddle,
        )
        SwitchRow(
            title = "安全模式",
            description = "安装前进行签名与来源校验",
            icon = Icons.Outlined.Info,
            checked = state.secureMode,
            onCheckedChange = { onStateChange(state.copy(secureMode = it)) },
            shape = StickyShapeLast,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "玻璃态卡片",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        GlassDemoCard(useBlur = state.useBlur)

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onOpenTheme,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = null)
            Text("  主题设置")
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onOpenInstaller,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text("安装流程演示")
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onOpenPreference,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text("Preference 设置页（Compose 嵌入）")
        }

        Spacer(Modifier.height(40.dp))
    }
}
