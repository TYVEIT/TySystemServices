package com.example.installerxui

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.example.installerxui.ui.widget.setting.ExpressiveCollapsingToolbarLayout
import com.example.installerxui.ui.widget.setting.ExpressiveCollapsingToolbarLayout_Two
import com.example.installerxui.ui.widget.setting.MainTitleImpl
import com.example.installerxui.ui.widget.setting.loadMainTitleImpl
import com.example.installerxui.ui.widget.setting.mainImplementation
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout

/**
 * 模仿 Android 12 Settings 的“大标题”基类（Kotlin 版），
 * 供主界面（主页 / 实验室）使用。
 *
 * 头部（AppBarLayout + ExpressiveCollapsingToolbarLayout）始终浮在内容上方，
 * 并通过 canDrag() = false 关闭随列表滚动自动折叠的行为，
 * 展开/收起由子类通过 [setToolbarExpanded] 主动控制（例如跳转二级页时收起）。
 *
 * 折叠效果由独立的 MainTitleImpl 控制（与 preference 页的 BigTitleImpl 互不影响）：
 * ONE -> activity_collapsing_toolbar_one（SCALE 滚动缩放，返回按钮自动注入）
 * TWO -> activity_collapsing_toolbar（FADE 淡出，返回按钮手写容器）
 *
 * 子类可通过 [setBackButton] 控制返回按钮显隐。
 */
abstract class CollapsingToolbarBaseActivity : AppCompatActivity() {

    private var contentFrame: FrameLayout? = null
    private var collapsingToolbarLayout: CollapsingToolbarLayout? = null
    private var appBarLayout: AppBarLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 恢复持久化的主界面大标题实现选择（进程重启后仍保留）
        mainImplementation = loadMainTitleImpl(this)
        // 先加载头部布局本身，子类再把内容放进 content_frame
        // 按独立 DropDownMenuWidget 的选择切换两套折叠效果：
        // ONE -> activity_collapsing_toolbar_one（SCALE 滚动缩放，自动注入返回按钮）
        // TWO -> activity_collapsing_toolbar（FADE 淡出，手写容器）
        val baseLayout = when (mainImplementation) {
            MainTitleImpl.ONE -> R.layout.activity_collapsing_toolbar_one
            MainTitleImpl.TWO -> R.layout.activity_collapsing_toolbar
        }
        super.setContentView(baseLayout)

        contentFrame = findViewById(R.id.content_frame)
        collapsingToolbarLayout = findViewById(R.id.collapsing_toolbar)
        appBarLayout = findViewById(R.id.app_bar)

        val toolbar = findViewById<Toolbar>(R.id.action_bar)
        setSupportActionBar(toolbar)

        appBarLayout?.let { disableAutoCollapse(it) }
    }

    /** 按当前主界面大标题实现分支设置返回按钮显隐（子类在 onCreate 中调用） */
    fun setBackButton(visible: Boolean) {
        when (mainImplementation) {
            MainTitleImpl.ONE -> findViewById<ExpressiveCollapsingToolbarLayout>(R.id.collapsing_toolbar)
                .setBackButtonEnabled(visible)
            MainTitleImpl.TWO -> findViewById<ExpressiveCollapsingToolbarLayout_Two>(R.id.collapsing_toolbar)
                .setBackButtonEnabled(visible)
        }
    }

    /** 子类调用 setContentView 时，把内容放到 content_frame，头部保持不变 */
    override fun setContentView(layoutResID: Int) {
        contentFrame?.let { frame ->
            frame.removeAllViews()
            LayoutInflater.from(this).inflate(layoutResID, frame, true)
        }
    }

    /** 设置展开态的大标题 */
    fun setLargeTitle(title: CharSequence) {
        collapsingToolbarLayout?.setTitle(title)
    }

    fun setLargeTitle(titleRes: Int) {
        setLargeTitle(getString(titleRes))
    }

    /** 主动展开 / 收起头部（Android 12 顶层页跳二级页时收起） */
    fun setToolbarExpanded(expanded: Boolean) {
        setToolbarExpanded(expanded, true)
    }

    fun setToolbarExpanded(expanded: Boolean, animate: Boolean) {
        appBarLayout?.setExpanded(expanded, animate)
    }

    private fun disableAutoCollapse(appBar: AppBarLayout) {
        val behavior = AppBarLayout.Behavior()
        behavior.setDragCallback(object : AppBarLayout.Behavior.DragCallback() {
            override fun canDrag(appBarLayout: AppBarLayout): Boolean = false
        })
        val lp = appBar.layoutParams as CoordinatorLayout.LayoutParams
        lp.behavior = behavior
    }
}
