package com.android.ty.systemservices.compose.preference

import android.os.Bundle
import android.view.View
import com.android.ty.systemservices.R

/**
 * 设置页 Fragment，从 XML 加载 Preference 层级。
 *
 * 继承 [BasePreferenceFragment] 自动获得全局对话框处理，
 * 以后新增 Dialog Preference 无需再关心对话框闪退问题。
 *
 * XML 中通过**全限定类名**引用自定义 Compose Preference：
 * - com.android.ty.systemservices.compose.preference.ComposeSwitchPreference
 * - com.android.ty.systemservices.compose.preference.ComposePreference
 * - com.android.ty.systemservices.compose.preference.ComposeListPreference
 * - com.android.ty.systemservices.compose.preference.ComposeEditPreference
 */
class SettingsFragment : BasePreferenceFragment() {
    companion object {
        private const val TAG = "SettingsFragment"

        const val KEY_OPEN_THEME = "open_theme"
        const val KEY_OPEN_INSTALLER = "open_installer"

        const val KEY_USE_BLUR = "use_blur"
        const val KEY_SECURE_MODE = "secure_mode"
        const val KEY_REPLY_ACTION = "reply_action"
        const val KEY_SIGNATURE = "signature"
        const val KEY_ABOUT = "about"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}
