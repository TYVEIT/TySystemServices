package com.android.ty.systemservices.compose

import android.app.Application
import android.os.Build
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
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // Android 12 以下：用 MonetCompat 回退 Material You 取色
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            MonetCompat.setup(this)
            MonetCompat.enablePaletteCompat()
            MonetCompat.getInstance().updateMonetColors()
        }
    }
}
