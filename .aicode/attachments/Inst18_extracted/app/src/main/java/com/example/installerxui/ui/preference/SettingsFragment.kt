package com.example.installerxui.ui.preference

import android.os.Bundle
import android.view.View
import com.example.installerxui.R

/**
 * 设置页 Fragment，从 XML 加载 Preference 层级。
 * XML 中通过全限定类名引用自定义 Compose Preference。
 */
class SettingsFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyTransparentListBackground(view)
    }

    companion object {
        const val KEY_USE_BLUR = "use_blur"
        const val KEY_SECURE_MODE = "secure_mode"
    }
}
