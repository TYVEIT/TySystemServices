package com.example.installerxui.ui.widget.setting

import android.content.Context
import androidx.annotation.StringRes
import com.example.installerxui.R

/**
 * 大标题折叠栏的两种实现方案（DropDownMenuWidget 切换目标）。
 *
 * - ONE：ExpressiveCollapsingToolbarLayout —— 返回按钮由子类 onFinishInflate 自动注入 Toolbar，
 *   布局中禁止手写 back_button_container。
 * - TWO：ExpressiveCollapsingToolbarLayout_Two —— 返回按钮渲染布局中手写的 back_button_container，
 *   布局必须保留该容器。
 *
 * 两个子类对外接口一致（均有 setBackButtonEnabled(Boolean)），切换只需换布局与 findViewById 泛型。
 */
enum class BigTitleImpl(@StringRes val labelRes: Int) {
    ONE(R.string.big_title_one),
    TWO(R.string.big_title_two),
    ORIGINAL(R.string.big_title_original),
}

/** 全局切换状态：默认 One（对应原 activity_preference / activity_collapsing_toolbar_one） */
var rootImplementation: BigTitleImpl = BigTitleImpl.ONE

private const val PREFS_NAME = "big_title_impl"
private const val KEY_IMPL = "impl"

/** 从 SharedPreferences 读取上次选择（未设置过返回 ONE） */
fun loadBigTitleImpl(context: Context): BigTitleImpl {
    val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_IMPL, null)
    return BigTitleImpl.entries.firstOrNull { it.name == name } ?: BigTitleImpl.ONE
}

/** 持久化当前选择，进程重启后仍保留 */
fun saveBigTitleImpl(context: Context, impl: BigTitleImpl) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_IMPL, impl.name)
        .apply()
    rootImplementation = impl
}
