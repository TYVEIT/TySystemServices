package com.android.ty.systemservices.compose.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 还原 InstallerX 的页面头部：
 * - 左上：圆角方形（squircle）返回按钮，按下时有弹性缩放手感 + 背景透明度变化
 * - 下方：超大号标题（headlineLarge）
 *
 * 对应截图中的"默认安装器"大标题 + 圆角方形返回箭头按钮。
 */
@Composable
fun PageHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // 返回按钮行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularBackButton(onClick = onBack)
        }

        Spacer(Modifier.height(4.dp))

        // 大标题
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(16.dp))
    }
}

/**
 * 圆角方形（squircle）返回按钮，带按压动画：
 * - 形状：圆角方形，对齐 InstallerX ExpressiveBackButton
 * - 按下时整体缩放（spring 弹性）
 * - 按下时背景透明度加深
 * - 箭头图标随按压有轻微反馈
 */
@Composable
fun CircularBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 弹性缩放
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(0.6f, 280f),
        label = "backButtonScale",
    )
    // 背景透明度：按下加深
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.8f else 0.6f,
        animationSpec = spring(0.6f, 280f),
        label = "backButtonAlpha",
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .scale(scale)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = backgroundAlpha),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}
