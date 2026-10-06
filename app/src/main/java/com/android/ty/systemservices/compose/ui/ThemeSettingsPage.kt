package com.android.ty.systemservices.compose.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.ty.systemservices.compose.theme.PaletteStyle
import com.android.ty.systemservices.compose.theme.PresetColors
import com.android.ty.systemservices.compose.theme.PresetBackgroundColors
import com.android.ty.systemservices.compose.theme.ThemeColorSpec
import com.android.ty.systemservices.compose.theme.ThemeMode

data class ThemeSettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val paletteStyle: PaletteStyle = PaletteStyle.Expressive,
    val colorSpec: ThemeColorSpec = ThemeColorSpec.SPEC_2025,
    val useDynamicColor: Boolean = false,
    val seedColor: Color = PresetColors.list.first().first, // 白色
    val backgroundColor: Color = Color(0xFFF5F0FA),
    val useDynamicBackgroundColor: Boolean = false,
)

/**
 * 主题设置页：还原 InstallerX 的主题偏好，
 * 含主题模式、配色风格、颜色规范版本、动态取色开关、种子色选择。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThemeSettingsPage(
    state: ThemeSettingsState,
    onStateChange: (ThemeSettingsState) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        PageHeader(title = "主题设置", onBack = onBack)

        SectionTitle("深色模式")
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.themeMode == mode,
                    onClick = { onStateChange(state.copy(themeMode = mode)) },
                    label = { Text(mode.displayName) },
                )
            }
        }

        SectionTitle("动态取色（Material You）")
        SwitchRow(
            title = "跟随系统壁纸取色",
            description = "Android 12+ 使用系统强调色，以下版本回退到种子色",
            icon = Icons.Outlined.Settings,
            checked = state.useDynamicColor,
            onCheckedChange = { onStateChange(state.copy(useDynamicColor = it)) },
        )

        SectionTitle("种子色")
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PresetColors.list.forEach { (color, name) ->
                ColorSwatch(
                    color = color,
                    name = name,
                    selected = state.seedColor == color,
                    onClick = { onStateChange(state.copy(seedColor = color)) },
                )
            }
        }

        SectionTitle("背景颜色")
        SwitchRow(
            title = "背景动态取色",
            description = "跟随种子色自动生成背景色，关闭后可手动选择",
            icon = Icons.Outlined.Settings,
            checked = state.useDynamicBackgroundColor,
            onCheckedChange = { onStateChange(state.copy(useDynamicBackgroundColor = it)) },
        )
        if (!state.useDynamicBackgroundColor) {
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PresetBackgroundColors.list.forEach { (color, name) ->
                    ColorSwatch(
                        color = color,
                        name = name,
                        selected = state.backgroundColor == color,
                        onClick = { onStateChange(state.copy(backgroundColor = color)) },
                    )
                }
            }
        }

        SectionTitle("配色风格 (Palette Style)")
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PaletteStyle.entries.forEach { style ->
                FilterChip(
                    selected = state.paletteStyle == style,
                    onClick = { onStateChange(state.copy(paletteStyle = style)) },
                    label = { Text(style.displayName) },
                )
            }
        }

        SectionTitle("颜色规范版本 (Color Spec)")
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeColorSpec.entries.forEach { spec ->
                FilterChip(
                    selected = state.colorSpec == spec,
                    onClick = { onStateChange(state.copy(colorSpec = spec)) },
                    label = { Text(spec.displayName) },
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 10.dp),
    )
}

/**
 * 种子色色块：用 Canvas 画三段圆弧（primary/tertiary/secondary container），
 * 还原 InstallerX ColorPalatteCard 的样式。选中时中心显示对勾。
 */
@Composable
private fun ColorSwatch(
    color: Color,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        val primary = color
        val secondary = color.copy(alpha = 0.7f)
        val tertiary = color.copy(alpha = 0.9f)

        Box(
            modifier = Modifier
                .size(64.dp)
                .background(color.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(color = primary.copy(alpha = 0.9f), startAngle = 180f, sweepAngle = 180f, useCenter = true)
                    drawArc(color = tertiary, startAngle = 90f, sweepAngle = 90f, useCenter = true)
                    drawArc(color = secondary, startAngle = 0f, sweepAngle = 90f, useCenter = true)
                }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
