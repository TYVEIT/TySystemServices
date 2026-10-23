package com.example.installerxui.ui.widget.setting

import android.content.Context
import androidx.annotation.StringRes
import com.example.installerxui.R

/**
 * 主界面（主页 / 实验室）独立的大标题折叠效果实现。
 *
 * 与 preference 页的 [BigTitleImpl] 互不影响，由独立的 DropDownMenuWidget 控制。
 *
 * - ONE：ExpressiveCollapsingToolbarLayout —— 返回按钮自动注入，折叠效果 TITLE_COLLAPSE_MODE_SCALE（滚动缩放）。
 * - TWO：ExpressiveCollapsingToolbarLayout_Two —— 返回按钮手写容器，折叠效果 TITLE_COLLAPSE_MODE_FADE（淡出）。
 */
enum class MainTitleImpl(@StringRes val labelRes: Int) {
    ONE(R.string.main_big_title_one),
    TWO(R.string.main_big_title_two),
}

/** 全局切换状态：默认 One（对应 activity_collapsing_toolbar_one） */
var mainImplementation: MainTitleImpl = MainTitleImpl.ONE

private const val PREFS_NAME = "main_title_impl"
private const val KEY_IMPL = "impl"

/** 从 SharedPreferences 读取上次选择（未设置过返回 ONE） */
fun loadMainTitleImpl(context: Context): MainTitleImpl {
    val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_IMPL, null)
    return MainTitleImpl.entries.firstOrNull { it.name == name } ?: MainTitleImpl.ONE
}

/** 持久化当前选择，进程重启后仍保留 */
fun saveMainTitleImpl(context: Context, impl: MainTitleImpl) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_IMPL, impl.name)
        .apply()
    mainImplementation = impl
}
