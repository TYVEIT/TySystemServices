package com.android.ty.systemservices

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.android.ty.systemservices.compose.ui.widget.setting.BigTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.ExpressiveCollapsingToolbarLayout
import com.android.ty.systemservices.compose.ui.widget.setting.ExpressiveCollapsingToolbarLayout_Two
import com.android.ty.systemservices.compose.ui.widget.setting.MainTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.loadBigTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.loadMainTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.mainImplementation
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout

/**
 * 头部（大标题）采用哪一套实现。
 *
 * 两套折叠效果由两个独立的持久化设置控制，互不影响：
 * - [MAIN]：主界面体系，[MainTitleImpl] 两档（SCALE 缩放 / FADE 淡出），布局 160dp；
 * - [PREFERENCE]：Preference 页体系，[BigTitleImpl] 三档（含 ORIGINAL 普通顶栏），布局 200dp。
 */
enum class TitleStyle {
    MAIN,
    PREFERENCE,
}

/**
 * 模仿 Android 12 Settings 的“大标题”基类（全项目唯一实现）。
 *
 * 头部（AppBarLayout + CollapsingToolbarLayout）始终浮在内容上方，并通过 `canDrag() = false`
 * 关闭随列表滚动自动折叠的行为，展开/收起由子类通过 [setToolbarExpanded] 主动控制
 * （例如顶层页跳二级页时收起）。
 *
 * 子类通过构造参数 [titleStyle] 选择头部体系：
 * - 主界面（主页 / 实验室 / 开发调试 / 贡献者）传 [TitleStyle.MAIN]；
 * - Preference 页（[SettingsActivity] / OverlayActivity）用默认值 [TitleStyle.PREFERENCE]。
 *
 * 内容必须放进 `content_frame`：重写的 [setContentView] 只会替换内容区，
 * **不要**调用 Compose 的 `setContent {}`，那会替换掉整套布局（含标题栏）。
 */
abstract class CollapsingToolbarBaseActivity(
    private val titleStyle: TitleStyle = TitleStyle.PREFERENCE,
) : AppCompatActivity() {

    private var contentFrame: FrameLayout? = null
    private var collapsingToolbarLayout: CollapsingToolbarLayout? = null
    private var appBarLayout: AppBarLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 头部布局按对应体系的持久化选择决定；主界面体系还要把选择写进全局状态，
        // 供屏幕内其它组件读取（preference 体系由 XmlPreferenceActivity 自行维护）。
        val baseLayout = when (titleStyle) {
            TitleStyle.MAIN -> {
                val impl = loadMainTitleImpl(this)
                mainImplementation = impl
                when (impl) {
                    MainTitleImpl.ONE -> R.layout.activity_collapsing_toolbar_one
                    MainTitleImpl.TWO -> R.layout.activity_collapsing_toolbar
                }
            }

            TitleStyle.PREFERENCE -> when (loadBigTitleImpl(this)) {
                BigTitleImpl.ONE -> R.layout.activity_preference
                BigTitleImpl.TWO -> R.layout.activity_preference_two
                BigTitleImpl.ORIGINAL -> R.layout.activity_preference_original
            }
        }
        super.setContentView(baseLayout)

        contentFrame = findViewById(R.id.content_frame)
        collapsingToolbarLayout = findViewById(R.id.collapsing_toolbar)
        appBarLayout = findViewById(R.id.app_bar)

        val toolbar = findViewById<Toolbar>(R.id.action_bar)
        setSupportActionBar(toolbar)

        appBarLayout?.let { disableAutoCollapse(it) }
    }

    /** 子类调用 setContentView 时，把内容放到 content_frame，头部保持不变 */
    override fun setContentView(layoutResID: Int) {
        contentFrame?.let { frame ->
            frame.removeAllViews()
            LayoutInflater.from(this).inflate(layoutResID, frame, true)
        }
    }

    /** 设置展开态的大标题（ORIGINAL 档没有折叠栏，回退到顶部栏标题） */
    fun setLargeTitle(title: CharSequence) {
        val layout = collapsingToolbarLayout
        if (layout != null) {
            layout.setTitle(title)
        } else {
            supportActionBar?.title = title
        }
    }

    fun setLargeTitle(titleRes: Int) {
        setLargeTitle(getString(titleRes))
    }

    /**
     * 统一控制返回按钮显隐。
     *
     * 按实际拿到的折叠栏类型分支，而不是按配置项分支：
     * ORIGINAL 档（没有折叠栏）回退到系统返回箭头，
     * 这样即使配置项与实际布局不同步也不会空指针。
     */
    fun setBackButton(visible: Boolean) {
        when (val layout = collapsingToolbarLayout) {
            is ExpressiveCollapsingToolbarLayout -> layout.setBackButtonEnabled(visible)
            is ExpressiveCollapsingToolbarLayout_Two -> layout.setBackButtonEnabled(visible)
            else -> supportActionBar?.setDisplayHomeAsUpEnabled(visible)
        }
    }

    /** 主动展开 / 收起头部（Android 12 顶层页跳二级页时收起） */
    fun setToolbarExpanded(expanded: Boolean) {
        setToolbarExpanded(expanded, true)
    }

    fun setToolbarExpanded(expanded: Boolean, animate: Boolean) {
        appBarLayout?.setExpanded(expanded, animate)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
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
