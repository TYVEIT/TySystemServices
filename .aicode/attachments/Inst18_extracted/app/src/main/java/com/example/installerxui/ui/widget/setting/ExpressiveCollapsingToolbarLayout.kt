package com.example.installerxui.ui.widget.setting

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.compose.ui.platform.ComposeView
import com.example.installerxui.R
import com.example.installerxui.ui.theme.InstallerXTheme
import com.google.android.material.appbar.CollapsingToolbarLayout

/**
 * CollapsingToolbarLayout 的自定义子类：
 * 统一承载 Compose 的 ExpressiveBackButton，并提供开关控制返回按钮显示/隐藏。
 *
 * 使用该子类后，布局中**无需**再手写 back_button_container ComposeView，
 * 返回按钮由本类在 onFinishInflate 时自动注入到 Toolbar 起始位置。
 *
 * 用法（XmlPreferenceActivity）：
 * ```
 * findViewById<ExpressiveCollapsingToolbarLayout>(R.id.collapsing_toolbar)
 *     .setBackButtonEnabled(true)   // true 开启返回按钮，false 关闭返回按钮
 * ```
 */
class ExpressiveCollapsingToolbarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : CollapsingToolbarLayout(context, attrs, defStyleAttr) {

    init {
        // 新版：大标题随滚动缩放/平移到顶部栏
        titleCollapseMode = CollapsingToolbarLayout.TITLE_COLLAPSE_MODE_SCALE
    }

    private var backButtonContainer: ComposeView? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        val toolbar = findViewById<Toolbar>(R.id.action_bar) ?: return
        val density = resources.displayMetrics.density
        // 布局中无需手写容器：自动注入一个 48dp 的 ComposeView 到 Toolbar 起始位置
        backButtonContainer = ComposeView(context).apply {
            layoutParams = Toolbar.LayoutParams(
                (48 * density).toInt(),
                (48 * density).toInt(),
                Gravity.START or Gravity.CENTER_VERTICAL,
            ).apply {
                marginStart = (4 * density).toInt()
            }
            toolbar.addView(this)
        }
        backButtonContainer?.setContent {
            InstallerXTheme {
                ExpressiveBackButton(onClick = { (context as? Activity)?.finish() })
            }
        }
    }

    /** true 开启返回按钮，false 关闭返回按钮（隐藏按钮并禁用点击） */
    fun setBackButtonEnabled(enabled: Boolean) {
        backButtonContainer?.visibility = if (enabled) View.VISIBLE else View.GONE
    }
}
