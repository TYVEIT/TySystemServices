package com.android.ty.systemservices.compose.preference

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.preference.ListPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import com.android.ty.systemservices.R
import com.android.ty.systemservices.compose.theme.ThemeStateHolder
import com.android.ty.systemservices.compose.ui.CardPosition
import com.android.ty.systemservices.compose.ui.toShape

/**
 * 用 Compose 渲染的列表选择 Preference（继承 [ListPreference]）。
 *
 * 点击时弹出原生选择对话框（由 [BasePreferenceFragment.onDisplayPreferenceDialog] 处理），
 * 标题、摘要（当前选中值）、图标、右侧箭头全部由 Compose 绘制。
 *
 * 在 preference XML 中以**全限定类名**引用：
 * ```xml
 * <com.android.ty.systemservices.compose.preference.ComposeListPreference
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

    var leadingIcon: ImageVector? = null
    var iconRes: Int = 0
    var cardPosition: CardPosition = CardPosition.AUTO

    init {
        layoutResource = R.layout.preference_compose
        if (attrs != null) {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.ComposePreference)
            cardPosition = CardPosition.fromValue(
                ta.getInt(R.styleable.ComposePreference_cardPosition, CardPosition.AUTO.value)
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
        val composeView = holder.findViewById(R.id.compose_view) as ComposeView
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindow
        )
        val effective = if (cardPosition == CardPosition.AUTO) detectCardPosition() else cardPosition
        composeView.setContent {
            MaterialTheme(colorScheme = ThemeStateHolder.current) {
                val haptic = LocalHapticFeedback.current
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
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        leadingIcon?.let {
                            Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        if (iconRes != 0) {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title?.toString().orEmpty(), style = MaterialTheme.typography.bodyLarge)
                            val rawSummary: String = summary?.toString().orEmpty()
                            val currentEntry: String = entry?.toString().orEmpty()
                            val displaySummary: String = if (rawSummary.contains("%s")) {
                                rawSummary.replace("%s", currentEntry)
                            } else if (currentEntry.isNotEmpty()) {
                                currentEntry
                            } else {
                                rawSummary
                            }
                            if (displaySummary.isNotEmpty()) {
                                Text(
                                    displaySummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (isEnabled) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
