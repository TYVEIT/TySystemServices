package com.android.ty.systemservices

import android.os.Bundle
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 安全设置入口页，结构与 [SettingsActivity] 保持一致：
 * 用 [CollapsingToolbarBaseActivity] 的大标题体系，内容区放 [SecurityFragment]。
 */
class SecurityActivity : CollapsingToolbarBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 与 SettingsActivity 保持一致：内容铺到系统栏下面，insets 才会给出真实状态栏高度，
        // 大标题才能像设置页那样贴到状态栏下方而不是被顶开
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        setBackButton(BackButtonHelper.isFromOwnActivity(this))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.content_frame, SecurityFragment())
                .commit()
        }

        setLargeTitle(R.string.security_title)

        // activity_main 的根容器；Fragment 事务是异步的，这里仍然拿得到
        findViewById<View>(R.id.main)?.let { root ->
            ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
    }
}
