package com.android.ty.systemservices.compose.preference

import android.os.Bundle
import android.view.View
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import androidx.preference.ListPreference
import androidx.preference.ListPreferenceDialogFragmentCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * Preference Fragment 基类，提供全局对话框处理。
 *
 * 在 Compose 嵌入 Fragment 的架构中，[PreferenceFragmentCompat] 默认的
 * [onDisplayPreferenceDialog] 可能因 FragmentManager 嵌套层级问题导致闪退。
 * 此基类显式处理所有 Dialog Preference 的对话框弹出，子类无需再重写。
 *
 * 目前支持：
 * - [ListPreference] → [ListPreferenceDialogFragmentCompat]
 * - [EditTextPreference] → [EditTextPreferenceDialogFragmentCompat]
 *
 * 以后新增自定义 Dialog Preference 只需继承 [PreferenceDialogFragmentCompat]，
 * 并在此类的 [getDialogFragment] 中添加对应分支即可。
 */
abstract class BasePreferenceFragment : PreferenceFragmentCompat() {

    @Suppress("DEPRECATION")
    override fun onDisplayPreferenceDialog(preference: Preference) {
        // 防止重复弹出
        val fm = parentFragmentManager
        fm.findFragmentByTag(DIALOG_TAG)?.let { return }

        val dialogFragment = getDialogFragment(preference) ?: run {
            super.onDisplayPreferenceDialog(preference)
            return
        }
        // 关键：设置 targetFragment，PreferenceDialogFragmentCompat 通过它回调结果
        dialogFragment.setTargetFragment(this, 0)
        dialogFragment.show(fm, DIALOG_TAG)
    }

    /**
     * 根据 Preference 类型返回对应的对话框 Fragment。
     * 使用 newInstance(key) 模式创建，确保 ARG_KEY 正确设置。
     * 新增自定义 Dialog Preference 时在此方法中添加分支即可。
     */
    @Suppress("DEPRECATION")
    protected open fun getDialogFragment(preference: Preference): PreferenceDialogFragmentCompat? {
        return when (preference) {
            is EditTextPreference -> EditTextPreferenceDialogFragmentCompat.newInstance(preference.key)
            is ListPreference -> ListPreferenceDialogFragmentCompat.newInstance(preference.key)
            else -> null
        }
    }

    /**
     * 让 Preference 列表背景透明并移除默认分隔线，适配粘连卡片样式。
     * 子类如需自定义列表外观可重写此方法。
     */
    protected fun applyTransparentListBackground(view: View) {
        listView?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        (listView as? RecyclerView)?.let { rv ->
            repeat(rv.itemDecorationCount) { rv.removeItemDecorationAt(0) }
        }
    }

    companion object {
        private const val DIALOG_TAG = "androidx.preference.PreferenceFragment.DIALOG"
    }
}
