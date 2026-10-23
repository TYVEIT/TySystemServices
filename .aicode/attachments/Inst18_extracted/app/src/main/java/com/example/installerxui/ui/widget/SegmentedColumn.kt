package com.example.installerxui.ui.widget

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.installerxui.ui.theme.ConnectionRadius
import com.example.installerxui.ui.theme.CornerRadius

// 由 SegmentedColumn 注入、由 BaseWidget 消费，实现"卡片粘连"分段圆角
val LocalSegmentedItemShape = compositionLocalOf<Shape> { RoundedCornerShape(CornerRadius) }

class SegmentedColumnScope {
    internal val items = mutableListOf<SegmentedItem>()

    fun item(content: @Composable () -> Unit) {
        items.add(SegmentedItem.Simple(content))
    }

    fun expandableItem(
        expanded: Boolean,
        topContent: @Composable () -> Unit,
        bottomContent: @Composable () -> Unit,
    ) {
        items.add(SegmentedItem.Expandable(expanded, topContent, bottomContent))
    }
}

sealed interface SegmentedItem {
    data class Simple(val content: @Composable () -> Unit) : SegmentedItem
    data class Expandable(
        val expanded: Boolean,
        val topContent: @Composable () -> Unit,
        val bottomContent: @Composable () -> Unit,
    ) : SegmentedItem
}

private data class SlotData(
    val content: @Composable () -> Unit,
    val flatTop: Boolean,
    val flatBottom: Boolean,
    val visible: Boolean,
)

/**
 * 分段列表容器：把多个 item 拼成一张"粘连"的卡片。
 * 展开/收起用 animateContentSize 做高度动画，圆角用 animateDpAsState 平滑过渡。
 */
@Composable
fun SegmentedColumn(
    modifier: Modifier = Modifier,
    title: String = "",
    content: SegmentedColumnScope.() -> Unit,
) {
    val scope = SegmentedColumnScope().apply(content)

    Column(modifier = modifier.fillMaxWidth()) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
            )
        }

        // 扁平化：Expandable 拆成 header + body 两个渲染单元
        val slots = buildList {
            scope.items.forEach { item ->
                when (item) {
                    is SegmentedItem.Simple -> add(
                        SlotData(item.content, flatTop = false, flatBottom = false, visible = true),
                    )

                    is SegmentedItem.Expandable -> {
                        add(
                            SlotData(item.topContent, flatTop = false, flatBottom = item.expanded, visible = true),
                        )
                        add(
                            SlotData(item.bottomContent, flatTop = true, flatBottom = false, visible = item.expanded),
                        )
                    }
                }
            }
        }

        val visibleCount = slots.count { it.visible }

        Column(modifier = Modifier.animateContentSize()) {
            var visibleIndex = 0
            slots.forEach { slot ->
                if (!slot.visible) return@forEach

                val isFirst = visibleIndex == 0
                val isLast = visibleIndex == visibleCount - 1
                visibleIndex++

                val baseTopRadius = if (isFirst) CornerRadius else ConnectionRadius
                val baseBottomRadius = if (isLast) CornerRadius else ConnectionRadius
                val targetTopRadius = if (slot.flatTop) 0.dp else baseTopRadius
                val targetBottomRadius = if (slot.flatBottom) 0.dp else baseBottomRadius

                val currentTopRadius by animateDpAsState(
                    targetValue = targetTopRadius,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 800f),
                    label = "topRadius-$visibleIndex",
                )
                val currentBottomRadius by animateDpAsState(
                    targetValue = targetBottomRadius,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 800f),
                    label = "bottomRadius-$visibleIndex",
                )

                val shape = RoundedCornerShape(
                    topStart = currentTopRadius,
                    topEnd = currentTopRadius,
                    bottomStart = currentBottomRadius,
                    bottomEnd = currentBottomRadius,
                )

                CompositionLocalProvider(LocalSegmentedItemShape provides shape) {
                    Column(modifier = Modifier.padding(top = if (isFirst) 0.dp else 1.dp)) {
                        slot.content()
                    }
                }
            }
        }
    }
}
