package com.android.ty.systemservices.compose.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class InstallStage {
    RESOLVING, ANALYSING, INSTALLING, SUCCESS
}

/**
 * 安装流程演示：用 AnimatedContent 在各个阶段之间做 spring 过渡，
 * 还原 InstallerX 安装对话框的阶段性切换。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InstallerDemoPage(
    onBack: () -> Unit,
) {
    var stage by remember { mutableStateOf(InstallStage.RESOLVING) }
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        stage = InstallStage.RESOLVING
        delay(1200)
        stage = InstallStage.ANALYSING
        delay(1500)
        stage = InstallStage.INSTALLING
        for (i in 1..20) {
            progress = i / 20f
            delay(120)
        }
        stage = InstallStage.SUCCESS
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("安装流程") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    fadeIn(animationSpec = spring()) togetherWith fadeOut(animationSpec = spring())
                },
                label = "install_stage",
            ) { current ->
                when (current) {
                    InstallStage.RESOLVING -> StageContent(
                        icon = { CircularProgressIndicator() },
                        title = "解析安装包",
                        subtitle = "正在识别 APK / APKS / XAPK 结构…",
                    )
                    InstallStage.ANALYSING -> StageContent(
                        icon = { CircularProgressIndicator() },
                        title = "分析包信息",
                        subtitle = "读取包名、版本、签名与权限…",
                    )
                    InstallStage.INSTALLING -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Icon(Icons.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("安装中", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("${(progress * 100).toInt()}%")
                    }
                    InstallStage.SUCCESS -> StageContent(
                        icon = {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(64.dp))
                        },
                        title = "安装完成",
                        subtitle = "应用已成功安装到设备",
                    )
                }
            }
        }
    }
}

@Composable
private fun StageContent(icon: @Composable () -> Unit, title: String, subtitle: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(32.dp),
    ) {
        Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) { icon() }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
