package com.example.installerxui

import android.os.Bundle
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import com.example.installerxui.ui.screen.PreferencesScreen
import com.example.installerxui.ui.theme.InstallerXTheme

/**
 * 主页：走 [CollapsingToolbarBaseActivity] 大标题体系，
 * 折叠效果由独立 MainTitleImpl（SCALE / FADE）控制，与 preference 页的 BigTitleImpl 互不影响。
 */
class MingActivity : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 顶层页：不需要返回按钮
        setBackButton(false)
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
            InstallerXTheme {
                PreferencesScreen()
            }
        }
    }
}
