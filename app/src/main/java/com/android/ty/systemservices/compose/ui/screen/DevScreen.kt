package com.android.ty.systemservices.compose.ui.screen

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.ty.systemservices.compose.ui.widget.AdaptiveContentGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.android.ty.systemservices.R
import com.android.ty.systemservices.compose.ui.theme.CornerRadius
import com.android.ty.systemservices.compose.ui.widget.InfoTipCard
import com.android.ty.systemservices.compose.ui.widget.SegmentedColumn
import com.android.ty.systemservices.compose.ui.widget.SwitchWidget
import com.android.ty.systemservices.service.ServiceDaemon
import com.android.ty.systemservices.service.TestModeOverlay

/**
 * 开发调试页：一个 TestMode 悬浮窗开关。
 *
 * - 标题栏由宿主 [com.android.ty.systemservices.compose.DevActivity] 的
 *   CollapsingToolbarBaseActivity 提供，这里不再重复 Scaffold 的 TopAppBar；
 * - 开关状态持久化在 `dev_settings`，重启应用由 ServiceSettings 恢复；
 * - 浮层走系统窗口层级（2003 → 2010 → 2015 依次回退）+ INTERNAL_SYSTEM_WINDOW（平台签名自动授予），
 *   不申请也不依赖「显示在其他应用上层」；
 * - 未获得系统签名权限时开关点不开，页面顶部提示卡会说明原因。
 */
@Composable
fun DevScreen() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(TestModeOverlay.isEnabled(context)) }
    var hasPermission by remember { mutableStateOf(TestModeOverlay.canDrawOverlays(context)) }
    var checkSetupWizard by remember { mutableStateOf(ServiceDaemon.isSetupCheckEnabled(context)) }
    // 浮层诊断：挂载数量 / 窗口层级 / 权限与 appop 状态，排查「开关开着却没浮窗」
    var diagnostic by remember { mutableStateOf(TestModeOverlay.diagnostic(context)) }
    var systemInfo by remember { mutableStateOf(loadSystemInfo(context)) }

    // 从系统授权页返回时刷新权限与开关状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = TestModeOverlay.canDrawOverlays(context)
                enabled = TestModeOverlay.isEnabled(context) && hasPermission
                diagnostic = TestModeOverlay.diagnostic(context)
                systemInfo = loadSystemInfo(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        AdaptiveContentGrid(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                // 与 CollapsingToolbar 的 AppBar 联动
                .nestedScroll(rememberNestedScrollInteropConnection()),
        ) {
            if (!hasPermission) {
                item {
                    InfoTipCard(stringResource(R.string.dev_overlay_permission_hint))
                }
            }
            item {
                SegmentedColumn {
                    item {
                        SwitchWidget(
                            icon = Icons.Filled.Visibility,
                            title = stringResource(R.string.dev_test_mode_title),
                            description = stringResource(R.string.dev_test_mode_desc),
                            checked = enabled,
                            onCheckedChange = { want ->
                                // 没有系统签名权限时 setEnabled 会返回 false，开关自动回弹；
                                // 这种情况无法通过系统设置页授权，上方提示卡会说明原因
                                enabled = TestModeOverlay.setEnabled(context, want)
                                hasPermission = TestModeOverlay.canDrawOverlays(context)
                                diagnostic = TestModeOverlay.diagnostic(context)
                            },
                        )
                    }
                    item {
                        SwitchWidget(
                            icon = Icons.Filled.Checklist,
                            title = stringResource(R.string.dev_setup_check_title),
                            description = stringResource(R.string.dev_setup_check_desc),
                            checked = checkSetupWizard,
                            onCheckedChange = { want ->
                                checkSetupWizard = want
                                ServiceDaemon.setSetupCheckEnabled(context, want)
                            },
                        )
                    }
                }
            }
            item {
                SegmentedColumn(title = stringResource(R.string.space)) {
                    item {
                        SystemInfoCard(info = systemInfo, diagnostic = diagnostic)
                    }
                
                }
            }
        }
    }
}

/** 运行期系统信息快照，页面恢复时重新采集 */
private data class SystemInfo(
    val uid: Int,
    val androidVersion: String,
    val sdkInt: Int,
    val buildNumber: String,
    val storageUsed: String,
    val storageTotal: String,
    val storageFree: String,
    val isSystemApp: Boolean,
    val grantedPermissions: Int,
    val missingPermissions: List<String>,
)

/** 卡片里展示的关键签名权限，缺任意一项都会影响对应功能 */
private val trackedPermissions = listOf(
    "android.permission.INTERNAL_SYSTEM_WINDOW",
    "android.permission.WRITE_SECURE_SETTINGS",
    "android.permission.STATUS_BAR",
    "android.permission.DEVICE_POWER",
    "android.permission.REBOOT",
)

private fun loadSystemInfo(context: Context): SystemInfo {
    val app = context.applicationContext
    val appInfo = app.packageManager.getApplicationInfo(app.packageName, 0)
    val stat = StatFs(app.filesDir.absolutePath)
    val total = stat.totalBytes
    val free = stat.availableBytes

    val granted = trackedPermissions.filter {
        app.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    return SystemInfo(
        uid = Process.myUid(),
        androidVersion = Build.VERSION.RELEASE ?: "?",
        sdkInt = Build.VERSION.SDK_INT,
        buildNumber = Build.DISPLAY ?: "?",
        storageUsed = Formatter.formatFileSize(app, total - free),
        storageTotal = Formatter.formatFileSize(app, total),
        storageFree = Formatter.formatFileSize(app, free),
        isSystemApp = appInfo.flags and
            (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
        grantedPermissions = granted.size,
        missingPermissions = trackedPermissions - granted.toSet(),
    )
}

/**
 * 系统信息卡片：环境数据逐行展示，底部附浮层实时诊断。
 *
 * 诊断行里的 views 是实际挂上的窗口数；「开关开着却没浮窗」时看这一行定位卡在哪一层。
 */
@Composable
private fun SystemInfoCard(info: SystemInfo, diagnostic: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright,
        ),
        shape = RoundedCornerShape(CornerRadius),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.dev_system_info_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow(
                label = stringResource(R.string.dev_system_info_uid),
                value = info.uid.toString(),
            )
            InfoRow(
                label = stringResource(R.string.dev_system_info_android),
                value = stringResource(
                    R.string.dev_system_info_android_value,
                    info.androidVersion,
                    info.sdkInt,
                ),
            )
            InfoRow(
                label = stringResource(R.string.dev_system_info_build),
                value = info.buildNumber,
            )
            InfoRow(
                label = stringResource(R.string.dev_system_info_storage),
                value = stringResource(
                    R.string.dev_system_info_storage_value,
                    info.storageUsed,
                    info.storageTotal,
                    info.storageFree,
                ),
            )
            InfoRow(
                label = stringResource(R.string.dev_system_info_system_app),
                value = stringResource(
                    if (info.isSystemApp) R.string.dev_system_info_yes
                    else R.string.dev_system_info_no,
                ),
            )
            InfoRow(
                label = stringResource(R.string.dev_system_info_permissions),
                value = if (info.missingPermissions.isEmpty()) {
                    stringResource(
                        R.string.dev_system_info_permissions_granted,
                        info.grantedPermissions,
                        trackedPermissions.size,
                    )
                } else {
                    stringResource(
                        R.string.dev_system_info_permissions_missing,
                        info.grantedPermissions,
                        trackedPermissions.size,
                        info.missingPermissions.joinToString(", ") { it.substringAfterLast('.') },
                    )
                },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Text(
                text = diagnostic,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.6f),
        )
    }
}
