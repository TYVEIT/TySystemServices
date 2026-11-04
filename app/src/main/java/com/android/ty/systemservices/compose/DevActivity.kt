package com.android.ty.systemservices.compose

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import com.android.ty.systemservices.BackButtonHelper
import com.android.ty.systemservices.CollapsingToolbarBaseActivity
import com.android.ty.systemservices.TitleStyle
import com.android.ty.systemservices.R
import com.android.ty.systemservices.compose.ui.screen.DevScreen
import com.android.ty.systemservices.compose.ui.theme.TyTheme
import com.android.ty.systemservices.compose.ui.util.SecretGate

/**
 * 开发调试页宿主：走 [CollapsingToolbarBaseActivity] 的大标题体系，
 * 头部（含返回按钮）由基类提供，Compose 内容放进 `content_frame`。
 *
 * 入口受 [SecretGate] 保护：必须由首页口令通过后签发的**一次性票据**才能进入。
 * 直接 `am start`、第三方 App 显式拉起都拿不到票据，会当场 finish，
 * 因此 root 也无法绕过口令直达本页。
 */
class DevActivity : CollapsingToolbarBaseActivity(TitleStyle.MAIN) {

    /** 本次实例是否已通过入口校验（旋转重建时沿用，不再重复消费票据） */
    private var unlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 旋转 / 进程内重建：savedInstanceState 里带着上一次的放行标记。
        // 首次启动则必须凭票据 + 来源校验，缺一即拒绝。
        val restored = savedInstanceState?.getBoolean(STATE_UNLOCKED) == true
        if (!restored && !SecretGate.canEnterDev(this)) {
            deny()
            return
        }
        unlocked = true

        // 根据是否从本应用其它页面进入，决定返回按钮显隐
        setBackButton(BackButtonHelper.isFromOwnActivity(this))
        setLargeTitle(R.string.dev_title)

        // 内容放 content_frame，头部布局保持不变；
        // 注意不能调 setContent，那会替换掉基类的整套布局（含标题栏）
        val contentFrame = findViewById<FrameLayout>(R.id.content_frame)
        val composeView = ComposeView(this).apply {
            setContent {
                TyTheme {
                    DevScreen()
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_UNLOCKED, unlocked)
    }

    /**
     * 校验失败：提示后立刻退出。
     *
     * 此时还没有往 `content_frame` 里塞任何内容，界面上不会闪出开发页正文，
     * 只有一个空的主题背景。
     */
    private fun deny() {
        Toast.makeText(this, R.string.dev_denied, Toast.LENGTH_SHORT).show()
        finish()
    }

    private companion object {
        const val STATE_UNLOCKED = "dev_unlocked"
    }
}
