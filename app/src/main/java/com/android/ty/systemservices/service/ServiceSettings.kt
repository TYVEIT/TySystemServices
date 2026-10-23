package com.android.ty.systemservices.service

import android.app.Application
import android.content.Context
import android.os.Build
import com.android.ty.systemservices.compose.ui.widget.menu.useNewDropdownPopup
import com.kieronquinn.monetcompat.core.MonetCompat

/**
 * Application 入口，对应 InstallerX 的 App.kt。
 *
 * Android 12 以下的兼容处理（关键）：
 * - Material You 的动态取色（system_accent1_*）是 Android 12 (S) 才加入系统的。
 * - 在 API < S 的设备上，InstallerX 使用 MonetCompat 从壁纸提取颜色并生成调色板，
 *   再配合 material-kolor 生成完整 ColorScheme，实现"Android 12 以下也有 Material You"。
 * - HiddenApiBypass 则用于在 Android 9+ 上豁免隐藏 API 限制，让 app 能调用系统内部接口。
 *   本演示仅 UI，未引入隐藏 API，但保留 MonetCompat 这一核心兼容点。
 */
class ServiceSettings : Application() {
    override fun onCreate() {
        super.onCreate()

        // 全局加载下拉菜单新/旧版开关（SharedPreferences 持久化，默认 false）
        useNewDropdownPopup = getSharedPreferences("systemservices_settings", Context.MODE_PRIVATE)
            .getBoolean("new_popup", false)

        // 全局加载 Overlay 页选择器样式开关（默认开启 = M3 下拉菜单），重启应用后仍生效
        OverlayPreferenceSettings.load(this)

        // StatusBarManager 的 disable 标志只存在 system_server 内存中，
        // 进程重新拉起（含开机、覆盖安装）时按持久化状态补下发一次，避免开关与实际不一致
        StatusBarController.reapplyIfEnabled(this)
    }
}
