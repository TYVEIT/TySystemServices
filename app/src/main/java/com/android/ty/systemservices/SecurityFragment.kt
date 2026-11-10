package com.android.ty.systemservices

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import com.android.ty.systemservices.compose.ui.preference.BasePreferenceFragment

/**
 * 安全设置页。
 *
 * 只做一件事：把两个 doze 相关的 Settings.Secure 开关暴露出来。
 * 状态以 Settings.Secure 为准，Preference 自身不持久化（setPersistent(false)），
 * 这样从别处（adb / 系统设置 / RRO）改动后回到本页能立刻看到最新值。
 */
class SecurityFragment : BasePreferenceFragment() {

    private lateinit var appContext: Context

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.security_preferences, rootKey)
        appContext = requireContext().applicationContext

        bindSecureSwitch(KEY_DOZE_ENABLED)
        bindSecureSwitch(KEY_DOZE_ALWAYS_ON)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyTransparentListBackground(view)
    }

    override fun onResume() {
        super.onResume()
        // 处理外部修改：进页面时按 Settings.Secure 实际值回填
        syncSecureSwitch(KEY_DOZE_ENABLED)
        syncSecureSwitch(KEY_DOZE_ALWAYS_ON)
    }

    /** 开关 <-> Settings.Secure：开启写 1，关闭写 0 */
    private fun bindSecureSwitch(key: String) {
        val pref = findPreference<TwoStatePreference>(key) ?: return
        pref.isPersistent = false
        pref.setOnPreferenceChangeListener { changed, newValue ->
            val value = newValue as? Boolean ?: false
            val label = labelOf(changed, key)
            try {
                val ok = Settings.Secure.putInt(
                    appContext.contentResolver,
                    key,
                    if (value) 1 else 0,
                )
                if (ok) {
                    toast(
                        appContext.getString(
                            if (value) R.string.pref_switch_on else R.string.pref_switch_off,
                            label,
                        ),
                    )
                    return@setOnPreferenceChangeListener true
                }
                toast(appContext.getString(R.string.pref_switch_failed, label))
                false
            } catch (e: Exception) {
                Log.e(TAG, "write Settings.Secure $key failed", e)
                toast(
                    appContext.getString(
                        R.string.pref_switch_failed_reason,
                        label,
                        e.message?.toString() ?: e.javaClass.simpleName,
                    ),
                )
                false
            }
        }
    }

    private fun syncSecureSwitch(key: String) {
        val pref = findPreference<TwoStatePreference>(key) ?: return
        val fallback = defaultOf(key)
        pref.isChecked = try {
            Settings.Secure.getInt(appContext.contentResolver, key, fallback) == 1
        } catch (e: Exception) {
            Log.e(TAG, "read Settings.Secure $key failed", e)
            fallback == 1
        }
    }

    /** 系统默认值：doze_enabled 默认开，doze_always_on 默认关 */
    private fun defaultOf(key: String): Int = when (key) {
        KEY_DOZE_ENABLED -> 1
        else -> 0
    }

    /** 提示文案优先用条目标题，避免把英文 key 直接抛给用户 */
    private fun labelOf(pref: Preference, fallbackKey: String): String {
        val title = pref.title
        return if (title.isNullOrEmpty()) fallbackKey else title.toString()
    }

    private fun toast(message: String?) {
        if (message == null) return
        val ctx = context ?: return
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "SecurityFragment"

        /** Settings.Secure.DOZE_ENABLED */
        const val KEY_DOZE_ENABLED = "doze_enabled"

        /** Settings.Secure.DOZE_ALWAYS_ON */
        const val KEY_DOZE_ALWAYS_ON = "doze_always_on"
    }
}
