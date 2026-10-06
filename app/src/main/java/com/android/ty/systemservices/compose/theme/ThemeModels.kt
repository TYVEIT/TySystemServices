package com.android.ty.systemservices.compose.theme

/**
 * 对应 InstallerX 的 PaletteStyle：material-kolor 支持的配色风格
 */
enum class PaletteStyle(val displayName: String, val supportsSpec2025: Boolean) {
    TonalSpot("Tonal Spot", false),
    Neutral("Neutral", false),
    Vibrant("Vibrant", true),
    Expressive("Expressive", true),
    Rainbow("Rainbow", true),
    FruitSalad("Fruit Salad", true),
    Monochrome("Monochrome", true),
    Fidelity("Fidelity", false),
    Content("Content", true),
}

/**
 * Material 3 颜色规范版本：2021（旧）与 2025（新，含 fixed color roles）
 */
enum class ThemeColorSpec(val displayName: String) {
    SPEC_2021("Spec 2021"),
    SPEC_2025("Spec 2025"),
}

enum class ThemeMode(val displayName: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}
