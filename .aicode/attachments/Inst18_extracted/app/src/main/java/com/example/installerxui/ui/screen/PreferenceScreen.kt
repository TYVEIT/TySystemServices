package com.example.installerxui.ui.screen

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.installerxui.R
import com.example.installerxui.ui.widget.setting.ExpressiveBackButton

/**
 * 主页 PreferencesScreen 的复制副本，项目自定义组件统一使用全限定类名引用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferenceScreen() {
    val context = LocalContext.current
    var adbVerify by remember { mutableStateOf(false) }
    var internetAccess by remember { mutableStateOf(true) }
    var ignoreBattery by remember { mutableStateOf(false) }
    var hideLauncherIcon by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_preferences)) },
                navigationIcon = {
                    ExpressiveBackButton(
                        onClick = { (context as? Activity)?.finish() },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item {
                com.example.installerxui.ui.widget.StatusCard(
                    appName = stringResource(R.string.status_app_name),
                    version = stringResource(R.string.status_version),
                    useBlur = true,
                )
            }
            item {
                com.example.installerxui.ui.widget.InfoTipCard(stringResource(R.string.tip_default))
            }

            item {
                com.example.installerxui.ui.widget.SegmentedColumn(title = stringResource(R.string.section_personalization)) {
                    item {
                        com.example.installerxui.ui.widget.NavigationItemWidget(
                            icon = Icons.Filled.Palette,
                            title = stringResource(R.string.theme_settings),
                            description = stringResource(R.string.theme_settings_desc),
                            onClick = {},
                        )
                    }
                    item {
                        com.example.installerxui.ui.widget.NavigationItemWidget(
                            icon = Icons.Filled.Settings,
                            title = stringResource(R.string.installer_settings),
                            description = stringResource(R.string.installer_settings_desc),
                            onClick = {},
                        )
                    }
                    item {
                        com.example.installerxui.ui.widget.NavigationItemWidget(
                            icon = Icons.Filled.Delete,
                            title = stringResource(R.string.uninstaller_settings),
                            description = stringResource(R.string.uninstaller_settings_desc),
                            onClick = {},
                        )
                    }
                }
            }

            item {
                com.example.installerxui.ui.widget.SegmentedColumn(title = stringResource(R.string.section_basic)) {
                    item {
                        com.example.installerxui.ui.widget.SwitchWidget(
                            icon = Icons.Filled.Wifi,
                            title = stringResource(R.string.network_settings),
                            description = stringResource(R.string.network_settings_desc),
                            checked = internetAccess,
                            onCheckedChange = { internetAccess = it },
                        )
                    }
                    item {
                        com.example.installerxui.ui.widget.SwitchWidget(
                            icon = Icons.Filled.Shield,
                            title = stringResource(R.string.disable_adb_verify),
                            description = stringResource(R.string.disable_adb_verify_desc),
                            checked = adbVerify,
                            onCheckedChange = { adbVerify = it },
                        )
                    }
                    item {
                        com.example.installerxui.ui.widget.SwitchWidget(
                            icon = Icons.Filled.BatteryFull,
                            title = stringResource(R.string.ignore_battery),
                            description = stringResource(R.string.ignore_battery_desc),
                            checked = ignoreBattery,
                            onCheckedChange = { ignoreBattery = it },
                        )
                    }
                    item {
                        com.example.installerxui.ui.widget.SwitchWidget(
                            icon = Icons.Filled.VisibilityOff,
                            title = stringResource(R.string.hide_launcher_icon),
                            description = stringResource(R.string.hide_launcher_icon_desc),
                            checked = hideLauncherIcon,
                            onCheckedChange = { hideLauncherIcon = it },
                        )
                    }
                }
            }

            item {
                com.example.installerxui.ui.widget.SegmentedColumn(title = stringResource(R.string.section_animation)) {
                    expandableItem(
                        expanded = advancedExpanded,
                        topContent = {
                            com.example.installerxui.ui.widget.NavigationItemWidget(
                                icon = Icons.Filled.Build,
                                title = stringResource(R.string.advanced_options),
                                description = stringResource(R.string.advanced_options_desc),
                                onClick = { advancedExpanded = !advancedExpanded },
                            )
                        },
                        bottomContent = {
                            com.example.installerxui.ui.widget.BaseWidget(
                                icon = Icons.Filled.Build,
                                title = stringResource(R.string.expanded_item),
                                description = stringResource(R.string.expanded_item_desc),
                            )
                        },
                    )
                }
            }

            item {
                HighlightableCard()
            }
        }
    }
}

/**
 * 演示 InteractiveHighlight：点按时出现 AGSL 水波高亮。
 */
@Composable
private fun HighlightableCard() {
    val scope = rememberCoroutineScope()
    // RuntimeShader 需要 API 33（Android 13），低版本退化为普通卡片、无高亮
    val highlight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        remember(scope) { com.example.installerxui.ui.animation.InteractiveHighlight(scope) }
    } else {
        null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .then(highlight?.gestureModifier ?: Modifier)
            .then(highlight?.modifier ?: Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.TouchApp,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Column {
                Text(
                    text = stringResource(R.string.ripple_highlight),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.ripple_highlight_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
