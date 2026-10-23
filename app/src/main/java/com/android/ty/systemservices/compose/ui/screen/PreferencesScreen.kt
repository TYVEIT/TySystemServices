package com.android.ty.systemservices.compose.ui.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import com.android.ty.systemservices.compose.ui.preference.XmlPreferenceActivity
import com.android.ty.systemservices.compose.ui.widget.setting.BigTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.DropDownMenuWidget
import com.android.ty.systemservices.compose.ui.widget.setting.MainTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.loadBigTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.loadMainTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.saveBigTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.saveMainTitleImpl
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.ty.systemservices.compose.LabActivity
import com.android.ty.systemservices.R
import com.android.ty.systemservices.compose.ui.animation.InteractiveHighlight
import com.android.ty.systemservices.compose.ui.theme.CyanBlue
import com.android.ty.systemservices.compose.ui.widget.BaseWidget
import com.android.ty.systemservices.compose.ui.widget.BaseWidget_Seek
import com.android.ty.systemservices.compose.ui.widget.NavigationItemWidget
import com.android.ty.systemservices.compose.ui.widget.SegmentedColumn
import com.android.ty.systemservices.compose.ui.widget.StatusCard
import com.android.ty.systemservices.compose.ui.widget.SwitchWidget
import com.android.ty.systemservices.compose.ui.widget.SwitchOw
import com.android.ty.systemservices.compose.ui.widget.InfoTipCard
import com.android.ty.systemservices.compose.ui.widget.setting.ExpressiveBackButton
import com.android.ty.systemservices.compose.ui.widget.menu.useNewDropdownPopup
import com.android.ty.systemservices.compose.ui.widget.ExpandableCard
import com.android.ty.systemservices.service.OverlayPreferenceSettings

import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen() {
    val context = LocalContext.current
    // 启动时从 SharedPreferences 恢复下拉菜单新/旧版开关（默认 false）
    LaunchedEffect(Unit) {
        useNewDropdownPopup = context.getSharedPreferences("systemservices_settings", Context.MODE_PRIVATE)
            .getBoolean("new_popup", false)
    }
    var adbVerify by remember { mutableStateOf(false) }
    var internetAccess by remember { mutableStateOf(true) }
    var ignoreBattery by remember { mutableStateOf(false) }
    var hideLauncherIcon by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var advancedExpanded2 by remember { mutableStateOf(false) }
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
                    appName = stringResource(R.string.app_name),
                    version = stringResource(R.string.status_version),
                    useBlur = true,
                )
            }
            item {
                InfoTipCard(stringResource(R.string.app_name))
            }

            item {
                SegmentedColumn(title = stringResource(R.string.section_personalization)) {
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
                                description = stringResource(mainTitleImpl.labelRes),
                                choice = mainTitleImpl.ordinal,
                                data = MainTitleImpl.entries.map { stringResource(it.labelRes) },
                                onChoiceChange = { index ->
                                    mainTitleImpl = MainTitleImpl.entries[index]
                                    saveMainTitleImpl(context, mainTitleImpl)
                                    (context as? Activity)?.recreate()
                                },
                        )
                    }
                    //item {
                    //    NavigationItemWidget(
                    //        icon = Icons.Filled.Science,
                    //        title = stringResource(R.string.lab),
                    //        description = stringResource(R.string.lab_desc),
                    //        onClick = {
                    //            context.startActivity(Intent(context, LabActivity::class.java))
                    //        },
                    //    )
                    //}
                    //item {
                    //    NavigationItemWidget(
                    //        icon = Icons.Filled.Science,
                    //        title = stringResource(R.string.preference),
                    //        description = stringResource(R.string.preference_desc),
                    //        onClick = {
                    //            context.startActivity(Intent(context, XmlPreferenceActivity::class.java))
                    //        },
                    //    )
                    //}
                }
            }

            item {
                SegmentedColumn(title = stringResource(R.string.section_basic)) {
                    item {
                        SwitchWidget(
                            icon = Icons.Filled.ArrowDropDown,
                            title = stringResource(R.string.dropdown_popup_new),
                            description = stringResource(R.string.dropdown_popup_new_desc),
                            checked = useNewDropdownPopup,
                            onCheckedChange = { newValue ->
                                useNewDropdownPopup = newValue
                                context.getSharedPreferences("systemservices_settings", Context.MODE_PRIVATE)
                                    .edit().putBoolean("new_popup", newValue).apply()
                            },
                        )
                    }
                    item {
                        // 控制 overlay_preferences.xml 用哪种选择器：
                        // 开 → ComposeDropDownMenuPreference（M3 下拉菜单）
                        // 关 → ComposeListPreference（原生选择对话框）
                        SwitchWidget(
                            icon = Icons.Filled.Tune,
                            title = stringResource(R.string.overlay_dropdown_switch),
                            description = stringResource(R.string.overlay_dropdown_switch_desc),
                            checked = OverlayPreferenceSettings.useDropdown,
                            onCheckedChange = { newValue ->
                                OverlayPreferenceSettings.setDropdownEnabled(context, newValue)
                            },
                        )
                    }
                    item {
                        ExpressiveSeekBarItem()
                    }
                }
            }

            item {
                ExpandableCard(
                    title = stringResource(R.string.advanced_options),
                    description = stringResource(R.string.advanced_options_hint),
                    icon = Icons.Filled.Build,
                    expanded = advancedExpanded,
                    onExpandedChange = { advancedExpanded = it },
                ) {
                    BaseWidget(
                        icon = Icons.Filled.Build,
                        title = stringResource(R.string.expanded_item),
                        description = stringResource(R.string.expanded_item_desc),
                    )
                }
            }
            item {
                ExpandableCard(
                    title = stringResource(R.string.advanced_options),
                    description = stringResource(R.string.advanced_options_hint),
                    icon = Icons.Filled.Build,
                    expanded = advancedExpanded2,
                    onExpandedChange = { advancedExpanded2 = it },
                ) {
                    BaseWidget(
                        icon = Icons.Filled.Build,
                        title = stringResource(R.string.expanded_item),
                        description = stringResource(R.string.expanded_item_desc),
                    )
                }
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

/**
 * M3 滑杆条目：真正的排版全部交给 [BaseWidget_Seek]，
 * 这里只负责持有进度状态，保证与同组其它条目同一套边距 / 圆角 / 水波纹。
 */
@Composable
private fun ExpressiveSeekBarItem() {
    // 旋转屏幕后保留进度
    var progress by rememberSaveable { mutableFloatStateOf(40f) }

    BaseWidget_Seek(
        icon = Icons.Filled.Tune,
        title = stringResource(R.string.expressive_seekbar_title),
        description = stringResource(R.string.expressive_seekbar_desc),
        valueText = progress.roundToInt().toString(),
        progress = progress,
        onProgressChange = { progress = it },
        valueRange = 0f..100f,
        steps = 9,
    )
}
