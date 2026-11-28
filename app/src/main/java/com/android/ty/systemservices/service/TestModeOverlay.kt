package com.android.ty.systemservices.service

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.android.ty.systemservices.R

/**
 * TestMode 悬浮窗控制器。
 *
 * ## 只走系统层级，不申请「显示在其他应用上层」
 *
 * 窗口类型固定 [WindowManager.LayoutParams.TYPE_SYSTEM_ERROR]（2010）。
 * WMS 对它的校验项是 `INTERNAL_SYSTEM_WINDOW`——signature 级权限，
 * 平台签名的系统应用在安装时就被授予，开机、重启、覆盖安装都不会丢，
 * 也不会出现在「特殊应用权限 → 显示在其他应用之上」列表里。
 *
 * 换句话说：**这条路完全不经过 appop**。用户在设置里把那个开关关掉，
 * 对本浮层没有任何影响，代码里也不需要去改 appop 状态。
 *
 * ## 为什么不是 2014 / 2003
 *
 * - 2014（TYPE_STATUS_BAR_PANEL）：Android 12 起 `DisplayPolicy.validateAddingWindowLw()`
 *   对它直接返回 `ADD_INVALID_TYPE`，addView 必失败——开关会回弹；
 * - 2003（TYPE_SYSTEM_ALERT）：持有 INTERNAL_SYSTEM_WINDOW 时层级为 13，
 *   低于 Android 12 的通知面板（19），压不住 SystemUI；
 *   以前靠 `PRIVATE_FLAG_IS_ROUNDED_CORNERS_OVERLAY` 抬层，但 Android 12 把该标记
 *   改成了独立表面（`WindowToken.makeSurface()` 里 `setParent(null)`），
 *   窗口能挂上却脱离显示器合成树，表现为「开关开着、屏幕上看不到」。
 *
 * 2010 在层级表里是 26（Android 10）/ 27（Android 12），
 * 本身就高于通知面板（17/19）与导航栏（23/24），不需要任何私有标记。
 *
 * 不抢占焦点、不拦截触摸，只作为状态指示。
 *
 * 开关状态存在 `dev_settings/test_mode_enabled`，进程重启后由
 * [ServiceSettings.onCreate] 调 [restore] 自动恢复，因此重启应用仍然记得。
 */
object TestModeOverlay {

    private const val TAG = "TestModeOverlay"
    private const val PREFS_NAME = "dev_settings"

    /** 系统私有窗口权限：signature 级，平台签名的系统应用自动授予 */
    private const val PERM_INTERNAL_SYSTEM_WINDOW =
        "android.permission.INTERNAL_SYSTEM_WINDOW"

    /** 浮层数量：TestMode + 预览开发阶段 */
    private const val OVERLAY_COUNT = 2
    private const val KEY_ENABLED = "test_mode_enabled"

    /**
     * 守护状态的系统级镜像。
     *
     * system_server 侧 `OverlayBoot` 的心跳每 5 分钟读一次这个值：
     * 为 0 就完全不碰本应用，避免"开关已关、进程却被反复拉起"。
     */
    private const val GLOBAL_DAEMON_KEY = "ty_overlay_daemon"

    /** 当前挂着的所有浮层（TestMode + 预览开发阶段），空表示未显示 */
    private val overlayViews = mutableListOf<View>()

    /** 读取持久化的开关状态 */
    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    /**
     * 设置开关：写入持久化 + 立即生效。
     *
     * 注意返回值语义是**最终状态**（true = 现在正处于开启态），不是"操作是否成功"。
     * 早期版本直接 `return hide()` 返回了"移除成功"的 true，导致关掉之后
     * 调用方把 true 赋回开关状态，界面要等重新进页面才刷新。
     *
     * @return 最终是否处于开启态（缺少系统签名权限或浮层挂不上时都是 false）
     */
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        val app = context.applicationContext
        if (enabled && !canDrawOverlays(app)) {
            Log.w(TAG, "missing INTERNAL_SYSTEM_WINDOW permission")
            return false
        }

        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (!enabled) {
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()
            hide(app)
            // 先让 system_server 停止唤醒，再停本地守护，顺序不能反
            syncGlobalFlag(app, false)
            // 关掉开关就不再需要常驻守护：取消闹钟并停掉 service
            ServiceDaemon.stop(app)
            return false
        }

        // 先真正挂上浮层，成功才把状态记为开启，避免"开关显示开、浮层却没出现"
        val shown = show(app)
        prefs.edit().putBoolean(KEY_ENABLED, shown).apply()
        syncGlobalFlag(app, shown)
        // 开启后交给守护体系：START_STICKY + 闹钟 + system_server 心跳三重兜底
        if (shown) ServiceDaemon.start(app) else ServiceDaemon.stop(app)
        return shown
    }

    /** 进程启动时按持久化状态恢复浮层 */
    @JvmStatic
    fun restore(context: Context) {
        val app = context.applicationContext
        val enabled = isEnabled(app) && canDrawOverlays(app)
        if (enabled) show(app) else hide(app)
        // 每次进程启动同步一次，保证 system_server 心跳看到的状态与应用一致
        syncGlobalFlag(app, enabled)
    }

    /** 把开关状态镜像到 Settings.Global，供 system_server 心跳读取 */
    private fun syncGlobalFlag(context: Context, enabled: Boolean) {
        try {
            Settings.Global.putInt(
                context.contentResolver,
                GLOBAL_DAEMON_KEY,
                if (enabled) 1 else 0,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "write Settings.Global $GLOBAL_DAEMON_KEY failed", t)
        }
    }

    /**
     * 是否具备加窗能力。
     *
     * 只认系统私有窗口权限，**不看 appop**——那个开关与本浮层无关，
     * 也不应该成为开关回弹或恢复失败的理由。
     */
    fun canDrawOverlays(context: Context): Boolean = hasSystemWindowPermission(context)

    /** 是否持有系统私有窗口权限（签名为准，与 appop 无关） */
    private fun hasSystemWindowPermission(context: Context): Boolean =
        context.checkSelfPermission(PERM_INTERNAL_SYSTEM_WINDOW) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 当前浮层的诊断信息，开发调试页直接展示。
     *
     * `views=2/2` 是实际挂载的窗口数；`type` 是加窗用的层级；
     * `sysPerm` 是 INTERNAL_SYSTEM_WINDOW 是否授予。
     */
    @JvmStatic
    fun diagnostic(context: Context): String {
        val app = context.applicationContext
        return "views=${overlayViews.size}/$OVERLAY_COUNT" +
            " type=${overlayWindowType()}" +
            " sysPerm=${hasSystemWindowPermission(app)}"
    }

    /** 系统窗口类型，固定 2010；这条路径不需要 appop */
    private fun overlayWindowType(): Int = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR

    private fun show(context: Context): Boolean {
        // 完整挂上过就直接返回；只挂上一半（某次 addView 失败）则清掉重建，
        // 避免"永远少一个浮层"且后续 show 都直接返回 true
        if (overlayViews.size >= OVERLAY_COUNT) return true
        if (overlayViews.isNotEmpty()) hide(context)

        val wm = context.getSystemService(WindowManager::class.java) ?: return false
        val density = context.resources.displayMetrics.density

        // 第二个浮层挂在第一个下面，两行文字都带中英双语
        val specs = listOf(
            context.getString(R.string.dev_test_mode_overlay) to (48 * density).toInt(),
            previewText(context) to (92 * density).toInt(),
        )

        var addedAny = false
        val type = overlayWindowType()
        for ((text, offsetY) in specs) {
            val view = createLabel(context, text)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                // 不抢焦点、不拦触摸，纯粹作为指示层，不影响下面应用的交互
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = offsetY
            }
            val added = tryAdd(wm, view, params, text)
            if (added) {
                overlayViews.add(view)
                addedAny = true
            }
        }
        return addedAny
    }

    /** addView 的薄封装：失败只记日志，由调用方决定开关回弹 */
    private fun tryAdd(
        wm: WindowManager,
        view: View,
        params: WindowManager.LayoutParams,
        tag: String,
    ): Boolean = try {
        wm.addView(view, params)
        true
    } catch (e: Exception) {
        Log.e(TAG, "add overlay view failed (type=${params.type}): $tag", e)
        false
    }

    /** 预览浮层文案：本地化一行 + 英文一行，语言相同时只显示一行 */
    private fun previewText(context: Context): String {
        val localized = context.getString(R.string.dev_preview_overlay)
        val english = context.getString(R.string.dev_preview_overlay_en)
        return if (localized == english) localized else "$localized\n$english"
    }

    private fun createLabel(context: Context, text: String): View {
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            val padH = (12 * density).toInt()
            val padV = (6 * density).toInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16 * density
                setColor(0xCC000000.toInt())
            }
        }
    }

    private fun hide(context: Context): Boolean {
        if (overlayViews.isEmpty()) return true
        val wm = context.getSystemService(WindowManager::class.java)
        overlayViews.forEach { view ->
            try {
                wm?.removeView(view)
            } catch (e: Exception) {
                Log.w(TAG, "remove overlay view failed", e)
            }
        }
        overlayViews.clear()
        return true
    }
}
