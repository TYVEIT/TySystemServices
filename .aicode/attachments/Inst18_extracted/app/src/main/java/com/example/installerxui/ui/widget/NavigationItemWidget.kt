package com.example.installerxui.ui.widget

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun NavigationItemWidget(
    icon: ImageVector,
    title: String,
    description: String? = null,
    onClick: () -> Unit,
) {
    BaseWidget(
        icon = icon,
        title = title,
        description = description,
        onClick = onClick,
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
        },
    )
}
