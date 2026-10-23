package com.android.ty.systemservices.compose

import android.os.Bundle
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import com.android.ty.systemservices.R
import com.android.ty.systemservices.BackButtonHelper
import com.android.ty.systemservices.compose.ui.screen.PreferencesScreen
import com.android.ty.systemservices.compose.ui.theme.TyTheme

/**
 * 主页：走 [CollapsingToolbarBaseActivity] 大标题体系，
 * 折叠效果由独立 MainTitleImpl（SCALE / FADE）控制，与 preference 页的 BigTitleImpl 互不影响。
 */
class MingActivity : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 根据是否从本应用其它页面进入，决定返回按钮显隐
        setBackButton(BackButtonHelper.isFromOwnActivity(this))
        setLargeTitle(R.string.title_preferences)

        // 主页内容：ComposeView 放进 content_frame（无 Scaffold/TopAppBar，头部由基类承载）
        val contentFrame = findViewById<FrameLayout>(R.id.content_frame)
        val composeView = ComposeView(this)
        contentFrame.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        composeView.setContent {
            TyTheme {
                PreferencesScreen()
            }
        }
    }
}
