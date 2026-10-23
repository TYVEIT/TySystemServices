package com.example.installerxui.ui.preference

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import com.example.installerxui.R
import com.example.installerxui.ui.theme.CyanBlue
import com.example.installerxui.ui.theme.InstallerXTheme
import com.example.installerxui.ui.widget.BaseWidget
import com.example.installerxui.ui.widget.CardPosition
import com.example.installerxui.ui.widget.LocalSegmentedItemShape
import com.example.installerxui.ui.widget.menu.GroupedDropdownMenuPopup
import com.example.installerxui.ui.widget.toShape

/**
 * 用 Compose 渲染的下拉菜单 Preference（继承 [ListPreference]）。
 *
 * 数据源复用 ListPreference 的 entries / entryValues（自动持久化到 SharedPreferences），
 * 点击时弹出 M3 下拉菜单（[GroupedDropdownMenuPopup] + [SelectableDropdownMenuItem]），
 * 而不是 ListPreference 默认的原生选择对话框；标题、摘要（当前选中值）、图标、
 * 右侧箭头全部由 Compose 绘制，统一使用 [BaseWidget] 青蓝色布局。
 *
 * 在 preference XML 中以**全限定类名**引用：
 * ```xml
 * <com.example.installerxui.ui.preference.ComposeDropDownMenuPreference
 *     android:key="resolution"
 *     android:title="分辨率"
 *     android:summary="当前：%s"
 *     android:entries="@array/resolution_entries"
 *     android:entryValues="@array/resolution_values"
 *     android:defaultValue="1080p"
 *     app:iconDrawable="@drawable/ic_list" />
 * ```
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class ComposeDropDownMenuPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ListPreference(context, attrs) {

    var iconRes: Int = 0
    var cardPosition: CardPosition = CardPosition.AUTO

    init {
        layoutResource = R.layout.preference_compose
        if (attrs != null) {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.ComposePreference)
            cardPosition = CardPosition.fromValue(
                ta.getInt(R.styleable.ComposePreference_cardPosition, CardPosition.AUTO.value),
            )
            iconRes = ta.getResourceId(R.styleable.ComposePreference_iconDrawable, 0)
            ta.recycle()
        }
    }

    private fun detectCardPosition(): CardPosition {
        val parent = parent as? PreferenceGroup ?: return CardPosition.SINGLE
        val count = parent.preferenceCount
        val index = (0 until count).firstOrNull { parent.getPreference(it) === this }
            ?: return CardPosition.SINGLE
        val isFirst = index == 0 || parent.getPreference(index - 1) is PreferenceCategory
        val isLast = index == count - 1 || parent.getPreference(index + 1) is PreferenceCategory
        return when {
            isFirst && isLast -> CardPosition.SINGLE
            isFirst -> CardPosition.FIRST
            isLast -> CardPosition.LAST
            else -> CardPosition.MIDDLE
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val composeView = holder.findViewById(R.id.compose_view) as? ComposeView ?: return
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindow,
        )
        val effective = if (cardPosition == CardPosition.AUTO) detectCardPosition() else cardPosition

        val entries: List<CharSequence> = getEntries()?.toList().orEmpty()
        val entryValues: List<CharSequence> = getEntryValues()?.toList().orEmpty()
        val rawSummary: String = summary?.toString().orEmpty()
        val currentEntry: String = entry?.toString().orEmpty()
        val displaySummary: String = if (rawSummary.contains("%s")) {
            rawSummary.replace("%s", currentEntry)
        } else if (currentEntry.isNotEmpty()) {
            currentEntry
        } else {
            rawSummary
        }
        val currentIndex = findIndexOfValue(value)

        composeView.setContent {
            InstallerXTheme {
                var expanded by remember { mutableStateOf(false) }
                val verticalPadding = when (effective) {
                    CardPosition.FIRST -> Modifier.padding(top = 4.dp)
                    CardPosition.MIDDLE -> Modifier
                    CardPosition.LAST -> Modifier.padding(bottom = 4.dp)
                    CardPosition.SINGLE -> Modifier.padding(vertical = 4.dp)
                    CardPosition.AUTO -> Modifier.padding(vertical = 4.dp)
                }
                CompositionLocalProvider(LocalSegmentedItemShape provides effective.toShape()) {
                    Box {
                        BaseWidget(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .then(verticalPadding),
                            iconRes = if (iconRes != 0) iconRes else null,
                            containerColor = CyanBlue.copy(alpha = 0.14f),
                            accentColor = CyanBlue,
                            title = title?.toString().orEmpty(),
                            description = displaySummary.takeIf { it.isNotEmpty() },
                            enabled = isEnabled,
                            onClick = { if (entries.isNotEmpty()) expanded = !expanded },
                            trailingContent = {
                                if (isEnabled) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                        GroupedDropdownMenuPopup(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            groupSizes = listOf(entries.size),
                            modifier = Modifier.align(Alignment.CenterStart),
                        ) { _, index, shape ->
                            val isSelected = index == currentIndex
                            SelectableDropdownMenuItem(
                                selected = isSelected,
                                onClick = {
                                    if (index < entryValues.size) {
                                        setValue(entryValues[index].toString())
                                    }
                                    expanded = false
                                },
                                text = { Text(text = entries[index].toString()) },
                                shapes = shape,
                            )
                        }
                    }
                }
            }
        }
    }
}
