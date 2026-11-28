package com.android.ty.systemservices.service

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Process
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
 * 窗口类型固定使用系统窗口，三类候选按 2003 → 2010 → 2015 逐个尝试：
 *
 * - 2003（TYPE_SYSTEM_ALERT）：常规系统提示层；
 * - 2010（TYPE_SYSTEM_ERROR）：层级更高；
 * - 2015（TYPE_SECURE_SYSTEM_OVERLAY）：层级最高（Android 10 为 31 / Android 12 为 33，
 *   高于通知面板 17/19、导航栏 23/24），且不受 appop 约束。
 *
 * WMS 对它们的校验项都是 `INTERNAL_SYSTEM_WINDOW`——signature 级权限，
 * 平台签名的系统应用在安装时就被授予，开机、重启、覆盖安装都不会丢，
 * 也不会出现在「特殊应用权限 → 显示在其他应用之上」列表里。
 *
 * ## 为什么前两个可能挂上却看不见
 *
 * 2003 / 2010 都属于 WMS 的 `isSystemAlertWindowType()`，窗口可见性额外绑在
 * `SYSTEM_ALERT_WINDOW` 这个 appop 上：`WindowState.initAppOpsState()` 读到 op
 * 不是 ALLOWED/DEFAULT 就 `setAppOpVisibilityLw(false)` 直接 `hide()`。
 * 结果是 addView 成功、`mHasSurface=true`，但 `isVisible=false`、屏幕上看不到。
 * 所以这两个类型在用之前先查 appop，不允许就直接跳过。
 *
 * 2015 不是 alert 类型，`checkAddPermission` 给它的 appOp 是 `OP_NONE`，
 * 不经过 appop，是前两个都不可用时的兜底。
 *
 * 2014（TYPE_STATUS_BAR_PANEL）不在候选里：Android 12 起
 * `DisplayPolicy.validateAddingWindowLw()` 对它返回 `ADD_INVALID_TYPE`，addView 必失败。
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

    /**
     * 窗口类型候选，按顺序尝试：2003 → 2010 → 2015。
     *
     * 2014（TYPE_STATUS_BAR_PANEL）不在列表里：Android 12 起
     * `DisplayPolicy.validateAddingWindowLw()` 对它返回 `ADD_INVALID_TYPE`，addView 必失败。
     */
    private val windowTypeCandidates = intArrayOf(
        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT, // 2003
        WindowManager.LayoutParams.TYPE_SYSTEM_ERROR, // 2010
        WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY, // 2015
    )

    /** 当前实际生效的窗口类型，全部候选都不可用时保留初始值 */
    private var activeType = windowTypeCandidates[0]

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
     * `sysPerm` 是 INTERNAL_SYSTEM_WINDOW 是否授予；
     * `shown` / `size` 是客户端侧看到的可见性与尺寸，用来区分「窗口没合成」和「视图本身是 0×0」。
     */
    @JvmStatic
    fun diagnostic(context: Context): String {
        val app = context.applicationContext
        val shown = overlayViews.count { it.isShown }
        val sizes = overlayViews.joinToString("/") { "${it.width}x${it.height}" }
        return "views=${overlayViews.size}/$OVERLAY_COUNT" +
            " type=$activeType" +
            " sysPerm=${hasSystemWindowPermission(app)}" +
            " shown=$shown/${overlayViews.size}" +
            " size=$sizes"
    }

    /** 这些类型的可见性还受 SYSTEM_ALERT_WINDOW appop 控制，挂上也未必看得见 */
    private fun isAppOpGatedType(type: Int): Boolean = when (type) {
        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
        WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
        -> true
        else -> false
    }

    /** 与 WMS 保持一致：MODE_DEFAULT 也算可见（initAppOpsState 会 startIfModeDefault） */
    private fun isAlertAppOpAllowed(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return true
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_DEFAULT
    }

    private fun show(context: Context): Boolean {
        // 完整挂上过就直接返回；只挂上一半（某次 addView 失败）则清掉重建，
        // 避免"永远少一个浮层"且后续 show 都直接返回 true
        if (overlayViews.size >= OVERLAY_COUNT) return true
        if (overlayViews.isNotEmpty()) hide(context)

        val wm = context.getSystemService(WindowManager::class.java) ?: return false
        for (type in windowTypeCandidates) {
            if (isAppOpGatedType(type) && !isAlertAppOpAllowed(context)) {
                Log.w(TAG, "skip window type $type: SYSTEM_ALERT_WINDOW not allowed")
                continue
            }
            if (addWindows(wm, context, type)) {
                activeType = type
                return true
            }
            // 这一轮挂了一半失败，先清干净再换下一个类型
            hide(context)
        }
        return false
    }

    /** 用指定类型挂上全部浮层；任一失败返回 false，已挂上的由调用方清理 */
    private fun addWindows(wm: WindowManager, context: Context, type: Int): Boolean {
        val density = context.resources.displayMetrics.density

        // 第二个浮层挂在第一个下面，两行文字都带中英双语
        val specs = listOf(
            context.getString(R.string.dev_test_mode_overlay) to (48 * density).toInt(),
            previewText(context) to (92 * density).toInt(),
        )

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
            if (!tryAdd(wm, view, params, text)) return false
            overlayViews.add(view)
        }
        return true
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
