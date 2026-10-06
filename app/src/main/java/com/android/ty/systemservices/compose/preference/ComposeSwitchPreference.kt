package com.android.ty.systemservices.compose.preference

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import androidx.preference.TwoStatePreference
import com.android.ty.systemservices.R
import com.android.ty.systemservices.compose.theme.ThemeStateHolder
import com.android.ty.systemservices.compose.ui.CardPosition
import com.android.ty.systemservices.compose.ui.toShape

/**
 * 用 Compose 渲染的开关 Preference。
 *
 * 继承 [TwoStatePreference] 以复用 Preference 框架的"持久化选中状态"能力，
 * 但标题/摘要/开关的绘制完全交给 Compose。
 *
 * 在 preference XML 中以**全限定类名**引用：
 * ```xml
 * <com.android.ty.systemservices.compose.preference.ComposeSwitchPreference
 *     android:key="use_blur"
 *     android:title="液态玻璃背景"
 *     android:summary="卡片使用流体动画 + 半透明模糊"
 *     android:defaultValue="true" />
 * ```
 */
class ComposeSwitchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TwoStatePreference(context, attrs) {

    // 用成员级 State 保存开关状态，避免每次 onBindViewHolder 新建状态导致错乱
    private var checkedState by mutableStateOf(isChecked)

    /** 图标 drawable 资源 ID，由 XML 属性 app:iconDrawable 指定 */
    var iconRes: Int = 0

    /** 卡片在粘连分组中的位置，由 XML 属性 app:cardPosition 指定，默认 AUTO 自动检测 */
    var cardPosition: CardPosition = CardPosition.AUTO

    init {
        layoutResource = R.layout.preference_compose
        if (attrs != null) {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.ComposePreference)
            val value = ta.getInt(R.styleable.ComposePreference_cardPosition, CardPosition.AUTO.value)
            cardPosition = CardPosition.fromValue(value)
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

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val composeView = holder.findViewById(R.id.compose_view) as ComposeView
        // 与 RecyclerView 回收兼容的策略：窗口分离时释放组合
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindow
        )
        // 同步 Preference 的真实选中状态到 Compose State
        checkedState = isChecked
        // AUTO 时根据父分组位置自动推断
        val effective = if (cardPosition == CardPosition.AUTO) detectCardPosition() else cardPosition
        composeView.setContent {
            MaterialTheme(colorScheme = ThemeStateHolder.current) {
                val haptic = LocalHapticFeedback.current
                // 粘连卡片的垂直内边距：首卡只留顶部、中卡不留、末卡只留底部、单卡上下都留
                val verticalPadding = when (effective) {
                    CardPosition.FIRST -> Modifier.padding(top = 4.dp)
                    CardPosition.MIDDLE -> Modifier
                    CardPosition.LAST -> Modifier.padding(bottom = 4.dp)
                    CardPosition.SINGLE -> Modifier.padding(vertical = 4.dp)
                    CardPosition.AUTO -> Modifier.padding(vertical = 4.dp)
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                    shape = effective.toShape(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(verticalPadding)
                        .padding(horizontal = 16.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { performClick() },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                            )
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (iconRes != 0) {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title?.toString().orEmpty(), style = MaterialTheme.typography.bodyLarge)
                            if (!summary.isNullOrEmpty()) {
                                Text(
                                    summary.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Switch(
                            checked = checkedState,
                            onCheckedChange = { performClick() },
                            colors = SwitchDefaults.colors(
                                checkedIconColor = MaterialTheme.colorScheme.primary,
                                uncheckedIconColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                            thumbContent = {
                                Icon(
                                    imageVector = if (checkedState) Icons.Filled.Check else Icons.Filled.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onClick() {
        super.onClick()
        // TwoStatePreference.onClick 已切换 isChecked 并持久化
        checkedState = isChecked
        notifyChanged()
    }
}
