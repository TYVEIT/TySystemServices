package com.example.installerxui.ui.screen

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.installerxui.R
import com.example.installerxui.ui.widget.InfoTipCard
import com.example.installerxui.ui.widget.SegmentedColumn
import com.example.installerxui.ui.widget.SwitchWidget
import com.example.installerxui.ui.widget.setting.DropDownMenuWidget
import com.example.installerxui.ui.widget.setting.ExpressiveBackButton

/** Root 实现（管理器）选项 */
enum class RootImplementation(@StringRes val labelRes: Int) {
    Magisk(R.string.root_magisk),
    KernelSU(R.string.root_kernelsu),
    APatch(R.string.root_apatch),
}

/**
 * 实验室页内容（不含标题栏）。
 * 标题栏由 LabActivity 按「大标题样式」选择渲染（新版 / 旧版 / 普通顶部栏）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabPageContent() {
    val context = LocalContext.current
    var moduleFlashEnabled by remember { mutableStateOf(false) }
    var rootImplementation by remember { mutableStateOf(RootImplementation.Magisk) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(rememberNestedScrollInteropConnection()),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item {
                InfoTipCard(stringResource(R.string.tip_lab))
            }
            item {
                SegmentedColumn(title = stringResource(R.string.section_module)) {
                    item {
                        SwitchWidget(
                        icon = Icons.Filled.Build,
                        title = stringResource(R.string.module_flash),
                        description = stringResource(R.string.module_flash_desc),
                        checked = moduleFlashEnabled,
                        onCheckedChange = { moduleFlashEnabled = it },
                        )
                    }
                    if (moduleFlashEnabled) {
                        item {
                            DropDownMenuWidget(
                                icon = Icons.Filled.Settings,
                                title = stringResource(R.string.root_implementation),
                                description = stringResource(rootImplementation.labelRes),
                                choice = rootImplementation.ordinal,
                                data = RootImplementation.entries.map { stringResource(it.labelRes) },
                                onChoiceChange = { index ->
                                    rootImplementation = RootImplementation.entries[index]
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
