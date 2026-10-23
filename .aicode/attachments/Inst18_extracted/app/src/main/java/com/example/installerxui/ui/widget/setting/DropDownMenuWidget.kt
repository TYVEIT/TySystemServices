// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2023-2026 iamr0s, InstallerX Revived contributors
// 适配版：BaseWidget 无 foreContent 插槽，改为调用处 Box 叠加菜单，不改动 BaseWidget 布局。
package com.example.installerxui.ui.widget.setting

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.installerxui.ui.widget.BaseWidget
import com.example.installerxui.ui.widget.menu.GroupedDropdownMenuPopup

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DropDownMenuWidget(
    icon: ImageVector? = null,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    choice: Int,
    data: List<String>,
    onChoiceChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        BaseWidget(
            icon = icon,
            title = title,
            description = description,
            enabled = enabled,
            isError = isError,
            onClick = { expanded = !expanded },
        )
        GroupedDropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            groupSizes = listOf(data.size),
            modifier = Modifier.align(Alignment.CenterStart),
        ) { _, index, shape ->
            val isSelected = index == choice
            SelectableDropdownMenuItem(
                selected = isSelected,
                onClick = {
                    onChoiceChange(index)
                    expanded = false
                },
                text = { Text(text = data[index]) },
                shapes = shape,
            )
        }
    }
}
