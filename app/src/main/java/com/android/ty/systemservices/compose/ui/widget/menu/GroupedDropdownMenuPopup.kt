// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 InstallerX Revived contributors
package com.android.ty.systemservices.compose.ui.widget.menu

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 下拉菜单版本开关：true 用新版（可滚动），false 用旧版（不可滚动） */
var useNewDropdownPopup by mutableStateOf(false)
/**
 * The shared layout shell for expressive dropdown menus.
 *
 * Each non-empty group is rendered as a separate Material 3 menu group. The
 * caller owns the menu item content and behavior, while this component keeps
 * the popup and group/item shape behavior consistent across screens.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GroupedDropdownMenuPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    groupSizes: List<Int>,
    modifier: Modifier = Modifier,
    itemContent: @Composable (groupIndex: Int, itemIndex: Int, shapes: MenuItemShapes) -> Unit,
) {
    val scrollState = rememberScrollState()
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = if (useNewDropdownPopup) {
            modifier
                .padding(vertical = 0.dp)
                .verticalScroll(scrollState)
        } else {
            modifier
        },
    ) {
        val nonEmptyGroups = groupSizes.mapIndexedNotNull { groupIndex, itemCount ->
            itemCount.takeIf { it > 0 }?.let { groupIndex to it }
        }
        nonEmptyGroups.forEachIndexed { renderedGroupIndex, (groupIndex, itemCount) ->
            if (renderedGroupIndex > 0) {
                Spacer(modifier = Modifier.height(2.dp))
            }
            DropdownMenuGroup(
                // Group shapes must describe the group's position in the whole popup.
                // Using standalone shapes for every group breaks the outer container's
                // leading/trailing corners when multiple groups are present.
                shapes = MenuDefaults.groupShape(
                    index = renderedGroupIndex,
                    count = nonEmptyGroups.size,
                ),
            ) {
                repeat(itemCount) { itemIndex ->
                    itemContent(
                        groupIndex,
                        itemIndex,
                        MenuDefaults.itemShape(index = itemIndex, count = itemCount),
                    )
                }
            }
        }
    }
}
