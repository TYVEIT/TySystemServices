package com.android.ty.systemservices.compose.ui.preference

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import com.android.ty.systemservices.R
import com.android.ty.systemservices.compose.ui.theme.CyanBlue
import com.android.ty.systemservices.compose.ui.theme.TyTheme
import com.android.ty.systemservices.compose.ui.widget.CardPosition
import com.android.ty.systemservices.compose.ui.widget.ExpandableCard
import com.android.ty.systemservices.compose.ui.widget.LocalSegmentedItemShape
import com.android.ty.systemservices.compose.ui.widget.toShape

/**
 * 用 Compose 渲染的可展开卡片 Preference（[ExpandableCard] 的 Preference 版本）。
 *
 * 头部沿用 Preference 框架的 title / summary（`android:title` / `android:summary`），
 * 展开后的内容文字由自定义属性 `app:otitle` / `app:osummary` 提供，
 * 因此同一个组件既能作为普通设置项，也能承载一段可折叠的说明文字。
 *
 * 在 preference XML 中以**全限定类名**引用：
 * ```xml
 * <com.android.ty.systemservices.compose.ui.preference.ExpandableCardPreference
 *     android:key="advanced_options"
 *     android:title="高级选项"
 *     android:summary="点击展开 / 收起"
 *     app:iconDrawable="@drawable/ic_settings_24dp"
 *     app:otitle="展开的子项"
 *     app:osummary="这是展开后显示的内容" />
 * ```
 *
 * 展开状态写在 `SharedPreferences`（`<key>_expanded`）里，滚动复用与旋转屏幕后都能保持。
 */
class ExpandableCardPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    /** 图标 drawable 资源 ID，由 XML 属性 app:iconDrawable 指定 */
    var iconRes: Int = 0

    /** 卡片在粘连分组中的位置，由 XML 属性 app:cardPosition 指定，默认 AUTO 自动检测 */
    var cardPosition: CardPosition = CardPosition.AUTO

    /** 展开区主文字，由 XML 属性 app:otitle 指定 */
    var contentTitle: String = ""

    /** 展开区副文字，由 XML 属性 app:osummary 指定 */
    var contentSummary: String = ""

    /**
     * 展开状态。放在成员里而非 composable 内部 remember，
     * 这样 ComposeView 被 RecyclerView 回收重建后状态不会丢。
     */
    private val expandedState = mutableStateOf(false)

    /** 展开状态持久化用的 key，避免占用 Preference 自身的 key */
    private val expandedPrefKey: String
        get() = "${key ?: "expandable_card"}_expanded"

    init {
        layoutResource = R.layout.preference_compose
        // 展开与否属于界面状态，不占用 Preference 自己的持久化 key
        isPersistent = false
        if (attrs != null) {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.ExpandableCardPreference)
            cardPosition = CardPosition.fromValue(
                ta.getInt(R.styleable.ExpandableCardPreference_cardPosition, CardPosition.AUTO.value),
            )
            iconRes = ta.getResourceId(R.styleable.ExpandableCardPreference_iconDrawable, 0)
            contentTitle = ta.getString(R.styleable.ExpandableCardPreference_otitle).orEmpty()
            contentSummary = ta.getString(R.styleable.ExpandableCardPreference_osummary).orEmpty()
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

        // 安全获取 ComposeView，找不到时直接返回，避免 ClassCastException / NullPointerException
        val composeView = holder.findViewById(R.id.compose_view) as? ComposeView ?: return

        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindow,
        )
        // 恢复上次的展开状态（滚动复用 / 旋转屏幕 / 重新进入页面）
        expandedState.value = sharedPreferences?.getBoolean(expandedPrefKey, false) ?: expandedState.value
        // AUTO 时根据父分组位置自动推断
        val effective = if (cardPosition == CardPosition.AUTO) detectCardPosition() else cardPosition
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
                // 展开内容与头部标题左对齐：有图标时标题从 16 + 24 + 16 开始
                val contentStart = if (iconRes != 0) 56.dp else 16.dp
                CompositionLocalProvider(LocalSegmentedItemShape provides effective.toShape()) {
                    ExpandableCard(
                        expanded = expandedState.value,
                        onExpandedChange = { newValue ->
                            expandedState.value = newValue
                            sharedPreferences?.edit()?.putBoolean(expandedPrefKey, newValue)?.apply()
                        },
                        title = title?.toString().orEmpty(),
                        description = summary?.toString().takeIf { !it.isNullOrEmpty() },
                        iconRes = if (iconRes != 0) iconRes else null,
                        shape = effective.toShape(),
                        containerColor = CyanBlue.copy(alpha = 0.14f),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .then(verticalPadding),
                    ) {
                        ExpandableCardContent(
                            title = contentTitle,
                            summary = contentSummary,
                            startPadding = contentStart,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 展开区内容：只负责把 `app:otitle` / `app:osummary` 两段文字排出来。
 * 两者都为空时不留多余空白。
 */
@Composable
private fun ExpandableCardContent(
    title: String,
    summary: String,
    startPadding: Dp,
) {
    if (title.isEmpty() && summary.isEmpty()) return

    Column(
        modifier = Modifier.padding(
            start = startPadding,
            end = 16.dp,
            top = 4.dp,
            bottom = 12.dp,
        ),
    ) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (summary.isNotEmpty()) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = if (title.isNotEmpty()) 4.dp else 0.dp),
            )
        }
    }
}
