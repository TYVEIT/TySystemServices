package com.example.installerxui.ui.screen

import android.app.Activity
import android.content.Intent
import android.os.Build
import com.example.installerxui.ui.preference.XmlPreferenceActivity
import com.example.installerxui.ui.widget.setting.BigTitleImpl
import com.example.installerxui.ui.widget.setting.DropDownMenuWidget
import com.example.installerxui.ui.widget.setting.MainTitleImpl
import com.example.installerxui.ui.widget.setting.loadBigTitleImpl
import com.example.installerxui.ui.widget.setting.loadMainTitleImpl
import com.example.installerxui.ui.widget.setting.saveBigTitleImpl
import com.example.installerxui.ui.widget.setting.saveMainTitleImpl
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.installerxui.LabActivity
import com.example.installerxui.R
import com.example.installerxui.ui.animation.InteractiveHighlight
import com.example.installerxui.ui.widget.BaseWidget
import com.example.installerxui.ui.widget.NavigationItemWidget
import com.example.installerxui.ui.widget.SegmentedColumn
import com.example.installerxui.ui.widget.StatusCard
import com.example.installerxui.ui.widget.SwitchWidget
import com.example.installerxui.ui.widget.SwitchOw
import com.example.installerxui.ui.widget.InfoTipCard
import com.example.installerxui.ui.widget.setting.ExpressiveBackButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen() {
    val context = LocalContext.current
    var adbVerify by remember { mutableStateOf(false) }
    var internetAccess by remember { mutableStateOf(true) }
    var ignoreBattery by remember { mutableStateOf(false) }
    var hideLauncherIcon by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var bigTitleImpl by remember { mutableStateOf(loadBigTitleImpl(context)) }
    var mainTitleImpl by remember { mutableStateOf(loadMainTitleImpl(context)) }

    // 主页大标题头部由 MingActivity 的 CollapsingToolbarBaseActivity 承载，
    // 这里不再包 Scaffold/TopAppBar；LazyColumn 必须带 nestedScroll 才能联动 AppBar 折叠。
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .nestedScroll(rememberNestedScrollInteropConnection()),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
            item {
                StatusCard(
                    appName = stringResource(R.string.status_app_name),
                    version = stringResource(R.string.status_version),
                    useBlur = true,
                )
            }
            item {
                InfoTipCard(stringResource(R.string.tip_default))
            }

            item {
                SegmentedColumn(title = stringResource(R.string.section_personalization)) {
                    item {
                        NavigationItemWidget(
                            icon = Icons.Filled.Palette,
                            title = stringResource(R.string.theme_settings),
                            description = stringResource(R.string.theme_settings_desc),
                            onClick = {},
                        )
                    }
                    item {
                        NavigationItemWidget(
                            icon = Icons.Filled.Settings,
                            title = stringResource(R.string.installer_settings),
                            description = stringResource(R.string.installer_settings_desc),
                            onClick = {},
                        )
                    }
                    item {
                        DropDownMenuWidget(
                                icon = Icons.Filled.Settings,
                                title = stringResource(R.string.big_title_style),
                                description = stringResource(bigTitleImpl.labelRes),
                                choice = bigTitleImpl.ordinal,
                                data = BigTitleImpl.entries.map { stringResource(it.labelRes) },
                                onChoiceChange = { index ->
                                    bigTitleImpl = BigTitleImpl.entries[index]
                                    saveBigTitleImpl(context, bigTitleImpl)
                                },
                        )
                    }
                    item {
                        DropDownMenuWidget(
                                icon = Icons.Filled.Settings,
                                title = stringResource(R.string.main_big_title_style),
                                description = stringResource(R.string.main_big_title_desc),
                                choice = mainTitleImpl.ordinal,
                                data = MainTitleImpl.entries.map { stringResource(it.labelRes) },
                                onChoiceChange = { index ->
                                    mainTitleImpl = MainTitleImpl.entries[index]
                                    saveMainTitleImpl(context, mainTitleImpl)
                                    (context as? Activity)?.recreate()
                                },
                        )
                    }
                    item {
                        NavigationItemWidget(
                            icon = Icons.Filled.Delete,
                            title = stringResource(R.string.uninstaller_settings),
                            description = stringResource(R.string.uninstaller_settings_desc),
                            onClick = {},
                        )
                    }
                    item {
                        NavigationItemWidget(
                            icon = Icons.Filled.Science,
                            title = stringResource(R.string.lab),
                            description = stringResource(R.string.lab_desc),
                            onClick = {
                                context.startActivity(Intent(context, LabActivity::class.java))
                            },
                        )
                    }
                    item {
                        NavigationItemWidget(
                            icon = Icons.Filled.Science,
                            title = stringResource(R.string.preference),
                            description = stringResource(R.string.preference_desc),
                            onClick = {
                                context.startActivity(Intent(context, XmlPreferenceActivity::class.java))
                            },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = stringResource(R.string.section_basic)) {
                    item {
                        SwitchWidget(
                            icon = Icons.Filled.Wifi,
                            title = stringResource(R.string.network_settings),
                            description = stringResource(R.string.network_settings_desc),
                            checked = internetAccess,
                            onCheckedChange = { internetAccess = it },
                        )
                    }
                    item {
                        SwitchWidget(
                            icon = Icons.Filled.Shield,
                            title = stringResource(R.string.disable_adb_verify),
                            description = stringResource(R.string.disable_adb_verify_desc),
                            checked = adbVerify,
                            onCheckedChange = { adbVerify = it },
                        )
                    }
                    item {
                        SwitchWidget(
                            icon = Icons.Filled.BatteryFull,
                            title = stringResource(R.string.ignore_battery),
                            description = stringResource(R.string.ignore_battery_desc),
                            checked = ignoreBattery,
                            onCheckedChange = { ignoreBattery = it },
                        )
                    }
                    item {
                        SwitchWidget(
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
                ExpandableCard(
                    expanded = advancedExpanded,
                    onExpandedChange = { advancedExpanded = it },
                )
            }

            item {
                HighlightableCard()
            }
        }
}

/**
 * 折叠卡片：参照示例应用（MDDemo）OverlayCardContent 的
 * Card + AnimatedVisibility(expandVertically/shrinkVertically) 实现，
 * 替代原版 SegmentedColumn.expandableItem 的动态插删写法，规避 Android 12 闪退。
 */
@Composable
private fun ExpandableCard(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    Card(
        onClick = { onExpandedChange(!expanded) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright,
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
                    Icon(
                        imageVector = Icons.Filled.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.advanced_options),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.advanced_options_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                BaseWidget(
                    icon = Icons.Filled.Build,
                    title = stringResource(R.string.expanded_item),
                    description = stringResource(R.string.expanded_item_desc),
                )
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
        remember(scope) { InteractiveHighlight(scope) }
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
