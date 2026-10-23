package com.android.ty.systemservices.service

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Overlay 页（`res/xml/overlay_preferences*.xml`）的选择器样式开关。
 *
 * - [useDropdown] = false（关闭）→ 加载 `overlay_preferences_list.xml`，三项使用
 *   [com.android.ty.systemservices.compose.ui.preference.ComposeListPreference]，
 *   点击弹出原生选择对话框。
 * - [useDropdown] = true（开启）→ 加载 `overlay_preferences.xml`，三项使用
 *   [com.android.ty.systemservices.compose.ui.preference.ComposeDropDownMenuPreference]，
 *   点击弹出 M3 下拉菜单。
 *
 * 开关值持久化在 `systemservices_settings`（key：[KEY_USE_DROPDOWN]），
 * 进程重启后由 [ServiceSettings.onCreate] 调 [load] 恢复，因此重启应用仍然记得。
 */
object OverlayPreferenceSettings {

    const val PREFS_NAME = "systemservices_settings"
    const val KEY_USE_DROPDOWN = "overlay_use_dropdown"

    /** 默认开启：保持 overlay 页原本的下拉菜单样式 */
    const val DEFAULT_USE_DROPDOWN = true

    /**
     * 供 Compose 读取的全局状态，切换后 Compose 侧开关立即刷新。
     * 只由 [setDropdownEnabled] / [load] 改写。
     */
    var useDropdown by mutableStateOf(DEFAULT_USE_DROPDOWN)
        private set

    /** 读取持久化值（Overlay 页加载 XML 时用，Activity 每次新建都会重新读） */
    @JvmStatic
    fun isDropdownEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_USE_DROPDOWN, DEFAULT_USE_DROPDOWN)

    /** 写入持久化值并同步内存状态 */
    @JvmStatic
    fun setDropdownEnabled(context: Context, enabled: Boolean) {
        useDropdown = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_USE_DROPDOWN, enabled)
            .apply()
    }

    /** 进程启动时把持久化值同步到内存状态 */
    @JvmStatic
    fun load(context: Context) {
        useDropdown = isDropdownEnabled(context)
    }
}
