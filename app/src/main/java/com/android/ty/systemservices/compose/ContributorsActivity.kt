package com.android.ty.systemservices.compose

import android.os.Bundle
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import com.android.ty.systemservices.BackButtonHelper
import com.android.ty.systemservices.CollapsingToolbarBaseActivity
import com.android.ty.systemservices.TitleStyle
import com.android.ty.systemservices.R
import com.android.ty.systemservices.compose.ui.screen.ContributorsScreen
import com.android.ty.systemservices.compose.ui.theme.TyTheme

/**
 * 贡献者列表宿主。
 *
 * 走 [CollapsingToolbarBaseActivity] 的大标题体系：标题栏与返回按钮由基类提供，
 * Compose 内容必须塞进 `content_frame`。
 *
 * 注意不能调 `setContent {}`：基类只重写了 `setContentView(resId)`，
 * Compose 的 `setContent` 会直接替换掉整套布局，大标题会随之消失。
 */
class ContributorsActivity : CollapsingToolbarBaseActivity(TitleStyle.MAIN) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 根据是否从本应用其它页面进入，决定返回按钮显隐
        setBackButton(BackButtonHelper.isFromOwnActivity(this))
        setLargeTitle(R.string.contributors_title)

        val contentFrame = findViewById<FrameLayout>(R.id.content_frame)
        val composeView = ComposeView(this).apply {
            setContent {
                TyTheme {
                    ContributorsScreen()
                }
            }
        }
        contentFrame.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }
}
