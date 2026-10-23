package com.example.installerxui.ui.widget.setting

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.compose.ui.platform.ComposeView
import com.example.installerxui.R
import com.example.installerxui.ui.theme.InstallerXTheme
import com.google.android.material.appbar.CollapsingToolbarLayout

/**
 * CollapsingToolbarLayout 的自定义子类（方案 Two：手写容器版）。
 *
 * 与 [ExpressiveCollapsingToolbarLayout]（One：自动注入版）的区别：
 * - One：onFinishInflate 时自动向 Toolbar 注入 48dp ComposeView，布局中**禁止**手写 back_button_container
 * - Two：渲染布局中**必须**手写 `@+id/back_button_container` 的 ComposeView，
 *   本类在 onFinishInflate 时找到该容器并 setContent 渲染 ExpressiveBackButton
 *
 * 对外接口与 One 完全一致（都有 setBackButtonEnabled(Boolean)），
 * 因此在 Activity 中切换 One/Two 时调用代码无需改动。
 *
 * 用法（布局 activity_collapsing_toolbar_down.xml）：
 * ```
 * <com.example.installerxui.ui.widget.setting.ExpressiveCollapsingToolbarLayout_Two ...>
 *     <androidx.appcompat.widget.Toolbar ...>
 *         <androidx.compose.ui.platform.ComposeView
 *             android:id="@+id/back_button_container"
 *             android:layout_width="48dp"
 *             android:layout_height="48dp"
 *             android:layout_gravity="start|center_vertical"
 *             android:layout_marginStart="4dp" />
 *     </androidx.appcompat.widget.Toolbar>
 * </com.example.installerxui.ui.widget.setting.ExpressiveCollapsingToolbarLayout_Two>
 * ```
 */
class ExpressiveCollapsingToolbarLayout_Two @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : CollapsingToolbarLayout(context, attrs, defStyleAttr) {

    init {
        // 旧版：大标题淡出，折叠后顶部栏标题淡入
        titleCollapseMode = CollapsingToolbarLayout.TITLE_COLLAPSE_MODE_FADE
    }

    private var backButtonContainer: ComposeView? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        // 布局中手写的返回按钮容器：找到后渲染 ExpressiveBackButton
        backButtonContainer = findViewById<ComposeView>(R.id.back_button_container) ?: return
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
