package com.android.ty.systemservices.compose.ui.preference

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
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import com.android.ty.systemservices.R
import com.android.ty.systemservices.compose.ui.theme.CyanBlue
import com.android.ty.systemservices.compose.ui.theme.TyTheme
import com.android.ty.systemservices.compose.ui.widget.BaseWidget
import com.android.ty.systemservices.compose.ui.widget.CardPosition
import com.android.ty.systemservices.compose.ui.widget.LocalSegmentedItemShape
import com.android.ty.systemservices.compose.ui.widget.toShape

/**
 * 用 Compose 渲染的多选列表 Preference（继承 [MultiSelectListPreference]）。
 *
 * 数据源复用 MultiSelectListPreference 的 entries / entryValues / values（Set<String>，
 * 由 Preference 框架持久化），点击时由 [ComposeMultiSelectListPreferenceDialogFragment]
 * 弹出 Compose 版多选面板；标题、摘要、图标、右侧箭头统一使用 [BaseWidget] 青蓝色布局，
 * 与 ComposeListPreference / ComposeDropDownMenuPreference 保持一致。
 *
 * 摘要规则：已选若干项时列出所选条目标签（顿号分隔）；一项未选时回退到 android:summary。
 *
 * 在 preference XML 中以**全限定类名**引用：
 * ```xml
 * <com.android.ty.systemservices.compose.ui.preference.ComposeMultiSelectListPreference
 *     android:key="multi_demo"
 *     android:title="多选示例"
 *     android:entries="@array/demo_multi_entries"
 *     android:entryValues="@array/demo_multi_values"
 *     app:iconDrawable="@drawable/ic_settings_24dp" />
 * ```
 */
class ComposeMultiSelectListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : MultiSelectListPreference(context, attrs) {

    /** 图标 drawable 资源 ID，由 XML 属性 app:iconDrawable 指定 */
    var iconRes: Int = 0

    /** 卡片在粘连分组中的位置，由 XML 属性 app:cardPosition 指定，默认 AUTO 自动检测 */
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

    /**
     * 自动检测卡片位置：根据在父 PreferenceGroup 中的索引和相邻 PreferenceCategory 边界推断。
     */
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

    /**
     * 把已选中的 value 映射成对应条目标签，顿号连接；无选中项时回退到 android:summary。
     */
    private fun buildDescription(): String? {
        val entryArray = entries
        val valueArray = entryValues
        if (entryArray == null || valueArray == null) {
            return summary?.toString()?.takeIf { it.isNotEmpty() }
        }
        val labels = values.orEmpty().mapNotNull { selected ->
            val index = valueArray.indexOfFirst { it?.toString() == selected }
            if (index in entryArray.indices) entryArray[index]?.toString() else null
        }
        return if (labels.isEmpty()) {
            summary?.toString()?.takeIf { it.isNotEmpty() }
        } else {
            labels.joinToString("、")
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        // 安全获取 ComposeView，找不到时直接返回，避免 ClassCastException / NullPointerException
        val composeView = holder.findViewById(R.id.compose_view) as? ComposeView ?: return

        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindow,
        )
        // AUTO 时根据父分组位置自动推断
        val effective = if (cardPosition == CardPosition.AUTO) detectCardPosition() else cardPosition
        val description = buildDescription()
        val displayTitle = title?.toString().orEmpty()
        val enabledState = isEnabled
        composeView.setContent {
            TyTheme {
                // 粘连卡片的垂直内边距：首卡只留顶部、中卡不留、末卡只留底部、单卡上下都留
                val verticalPadding = when (effective) {
                    CardPosition.FIRST -> Modifier.padding(top = 4.dp)
                    CardPosition.MIDDLE -> Modifier
                    CardPosition.LAST -> Modifier.padding(bottom = 4.dp)
                    CardPosition.SINGLE -> Modifier.padding(vertical = 4.dp)
                    CardPosition.AUTO -> Modifier.padding(vertical = 4.dp)
                }
                CompositionLocalProvider(LocalSegmentedItemShape provides effective.toShape()) {
                    BaseWidget(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .then(verticalPadding),
                        iconRes = if (iconRes != 0) iconRes else null,
                        containerColor = CyanBlue.copy(alpha = 0.14f),
                        accentColor = CyanBlue,
                        title = displayTitle,
                        description = description,
                        enabled = enabledState,
                        onClick = { performClick() },
                        trailingContent = {
                            if (enabledState) {
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
