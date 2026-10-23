package com.example.installerxui.ui.preference

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import com.example.installerxui.R
import com.example.installerxui.ui.theme.InstallerXTheme
import com.example.installerxui.ui.theme.CyanBlue
import com.example.installerxui.ui.widget.BaseWidget
import com.example.installerxui.ui.widget.CardPosition
import com.example.installerxui.ui.widget.LocalSegmentedItemShape
import com.example.installerxui.ui.widget.toShape

/**
 * 用 Compose 渲染的列表选择 Preference（继承 [ListPreference]）。
 *
 * 点击时弹出原生选择对话框（由 [BasePreferenceFragment.onDisplayPreferenceDialog] 处理），
 * 标题、摘要（当前选中值）、图标、右侧箭头全部由 Compose 绘制，统一使用 [BaseWidget] 布局。
 *
 * 在 preference XML 中以**全限定类名**引用：
 * ```xml
 * <com.example.installerxui.ui.preference.ComposeListPreference
 *     android:key="theme_mode"
 *     android:title="主题模式"
 *     android:summary="当前：%s"
 *     android:entries="@array/theme_mode_entries"
 *     android:entryValues="@array/theme_mode_values"
 *     android:defaultValue="auto"
 *     app:iconDrawable="@drawable/ic_list" />
 * ```
 */
class ComposeListPreference @JvmOverloads constructor(
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
        composeView.setContent {
            InstallerXTheme {
                val verticalPadding = when (effective) {
                    CardPosition.FIRST -> Modifier.padding(top = 4.dp)
                    CardPosition.MIDDLE -> Modifier
                    CardPosition.LAST -> Modifier.padding(bottom = 4.dp)
                    CardPosition.SINGLE -> Modifier.padding(vertical = 4.dp)
                    CardPosition.AUTO -> Modifier.padding(vertical = 4.dp)
                }
                val rawSummary: String = summary?.toString().orEmpty()
                val currentEntry: String = entry?.toString().orEmpty()
                val displaySummary: String = if (rawSummary.contains("%s")) {
                    rawSummary.replace("%s", currentEntry)
                } else if (currentEntry.isNotEmpty()) {
                    currentEntry
                } else {
                    rawSummary
                }
                CompositionLocalProvider(LocalSegmentedItemShape provides effective.toShape()) {
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
                        onClick = { performClick() },
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
                }
            }
        }
    }
}
