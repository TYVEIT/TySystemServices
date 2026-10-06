package com.android.ty.systemservices.compose.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf

/**
 * 全局主题状态持有器（单例）。
 *
 * MainActivity 在重组时把当前 ColorScheme 写入此对象，
 * 供 Preference 内部的 Compose 内容读取，从而让 Preference 页的 Compose 控件
 * 与主界面使用同一套配色（包括动态取色、种子色、配色风格）。
 *
 * 这是"Compose 嵌入 Preference"时让主题一致的关键桥梁。
 */
object ThemeStateHolder {
    val colorScheme = mutableStateOf<ColorScheme>(lightColorScheme())

    fun update(scheme: ColorScheme) {
        colorScheme.value = scheme
    }

    val current: ColorScheme
        get() = colorScheme.value
}
