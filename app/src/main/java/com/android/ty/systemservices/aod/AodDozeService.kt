package com.android.ty.systemservices.aod

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.dreams.DreamService
import android.text.format.DateFormat
import android.util.Log
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import com.android.ty.systemservices.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 熄屏显示（AOD）的 doze 组件。
 *
 * 系统把 framework-res 的 `config_dozeComponent` 指向本服务后，
 * 每次灭屏进入 doze 都会以 doze 模式绑定这里，由它负责在低功耗面板上作画，
 * 取代默认的 `com.android.systemui/.doze.DozeService`。
 *
 * 为什么直接继承 [DreamService] 而不是某个 `DozeService`：
 * AOSP 框架里**没有** `android.service.dreams.DozeService` 这个类，
 * `config_dozeComponent` 指向的组件（含 SystemUI 自己的实现）都是
 * `android.service.dreams.DreamService` 的子类；doze 相关能力
 * （`startDozing` / `setDozeScreenState` / `setDozeScreenBrightness`）是
 * DreamService 上的 @hide 方法，用反射调用，避免编译期依赖隐藏 API。
 * 之前继承了一个不存在的类，运行时类加载直接失败，表现就是熄屏后全黑。
 *
 * 关键约束：
 * - manifest 里必须声明 `BIND_DREAM_SERVICE`（signature 级）与 DreamService 的 intent-filter；
 * - 只有在 DreamManagerService 以 doze 模式绑定时 `startDozing()` 才会生效，
 *   否则 `setDozeScreenState()` 不产生任何效果（此时窗口本身仍然可见）；
 * - UI 用原生 View 而不是 Compose：doze 窗口不是 Activity，没有
 *   `ViewTreeLifecycleOwner`，Compose 需要手工挂 owner，一旦出问题就是灭屏后全黑。
 *
 * 交互：轻触屏幕 -> 唤醒到锁屏。
 */
class AodDozeService : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    /** 承载时钟/日期/电量的容器，进场动画作用在它身上，不与防烧屏位移打架 */
    private var stage: View? = null
    private var column: LinearLayout? = null
    private var dateView: TextView? = null
    private var batteryView: TextView? = null
    private var hintView: TextView? = null

    /** 手势传感器监听（tap / double tap / long press），doze 期间才注册 */
    private var sensorListener: SensorEventListener? = null
    /** 防止传感器连发 / 触摸与手势同时触发，重复走唤醒流程 */
    private var waking = false

    /**
     * 记录「这台设备到底有没有 DOZE 面板」。
     *
     * 必须用 device-protected 存储：服务声明了 directBootAware，
     * 开机未解锁时凭据加密的 SharedPreferences 根本打不开。
     */
    private val prefs by lazy {
        val ctx = createDeviceProtectedStorageContext() ?: this
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** 每分钟刷新日期 / 电量 / 防烧屏偏移 */
    private val ticker = object : Runnable {
        override fun run() {
            refreshSecondary()
            handler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")

        // 触摸由 AOD 自己消费，实现「轻触解锁」
        setInteractive(true)
        setFullscreen(true)
        // 必须保持屏幕常亮：doze 面板亮度由我们接管，若窗口不申请常亮，
        // 系统会在超时后直接熄屏，看起来同样是「全黑」
        setScreenBright(true)

        // 兜底：某些 ROM 的 dream 窗口带浅色 decor 背景，显式压成黑色
        window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        Log.i(TAG, "onDreamingStarted, canDoze=${readBool("canDoze")}, panelKnown=${dozePanelSupported()}")

        val content = buildContentView()
        setContentView(content)
        refreshSecondary()

        // 真正的 doze 面板下 SystemUI 本来就不画状态栏/导航栏；但我们会在不支持
        // DOZE 的机器上退回常亮显示，那时系统仍认为设备是醒着的，会把导航栏画出来。
        // 这里主动进 immersive 全屏，并在一段时间内反复压一次（系统栏可能在窗口
        // 上屏后又被系统重新显示）。
        applyImmersive()
        handler.postDelayed({ applyImmersive() }, 400L)
        handler.postDelayed({ applyImmersive() }, 1_200L)

        // 关键：等这一帧真正挂到窗口上再起动画。
        // 之前直接在 setContentView 后调 animate()，view 还没 attach，
        // 属性动画会直接跳到终值 —— 表现就是「没有动画」。
        content.post { playEnterAnimation() }

        handler.removeCallbacks(ticker)
        handler.post(ticker)

        // 抬手/双击/长按这些手势由 framework-res 里的 config_doze*SensorType 指定，
        // 系统只负责把 type 告诉 doze 组件，注册监听要自己做
        registerDozeSensors()

        if (dozePanelSupported()) {
            // 等入场动画收尾再切面板：面板进入低功耗后帧率被压低，动画会卡成几帧
            handler.postDelayed({ enterDozePanel() }, DOZE_ENTER_DELAY_MS)
            // 面板切换后回读显示状态：设备不支持 DOZE 时退回普通亮屏，至少保证可见
            handler.postDelayed({ verifyPanel() }, DOZE_VERIFY_DELAY_MS)
        } else {
            // 这台机器已经确认没有 DOZE 面板，直接常亮显示，不再白黑一下
            setScreenBright(true)
        }
    }

    /**
     * 熄屏进入时的进场动画，参数对齐 SystemUI 的 doze 过渡。
     *
     * SystemUI 的做法是把 `dozeAmount` 从 0 线性推到 1（`ScrimController` 里用的就是
     * `LinearInterpolator`），时长 1 秒上下，表现是「画面整体从全黑里慢慢浮出来」，
     * 不带缩放、不带弹跳。这里用同样的手感：
     *
     * - 内容 alpha 0 -> 1，900ms，线性；
     * - 同时从下往上收 10dp，位移量很小，只为了让淡入不显得「死」；
     * - 底部提示晚 60% 才起步，比主内容慢一档收尾。
     *
     * 之所以不用缩放/弹性曲线：系统 AOD 是「渐变出现」而不是「弹出来」，
     * 一旦带上缩放，观感就变成普通 App 的转场，和灭屏这件事对不上。
     */
    private fun playEnterAnimation() {
        val stage = this.stage ?: return
        stage.animate().cancel()
        stage.alpha = 0f
        stage.translationY = dp(ENTER_OFFSET_DP).toFloat()
        stage.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(ENTER_DURATION_MS)
            .setInterpolator(LinearInterpolator())
            .start()

        hintView?.let { hint ->
            hint.animate().cancel()
            hint.alpha = 0f
            hint.animate()
                .alpha(1f)
                .setStartDelay(ENTER_DURATION_MS * 6 / 10)
                .setDuration(ENTER_DURATION_MS * 4 / 10)
                .setInterpolator(LinearInterpolator())
                .start()
        }
    }

    /**
     * 隐藏状态栏与导航栏，做成 SystemUI AOD 那种「整块屏幕只剩时间和日期」的样子。
     *
     * 为什么不能只靠 `setFullscreen(true)`：
     * 真正的 doze 面板下 SystemUI 本来就不画系统栏；但我们会在不支持 DOZE 的机器
     * （虚拟机、部分 LCD 机型）上退回常亮显示，此时系统仍认为设备是醒着的，
     * 会把三键导航栏照常画出来，AOD 看起来就像个普通全屏页面。
     *
     * 这里对 API 30+ 用 WindowInsetsController，29 用旧版 systemUiVisibility；
     * 并且由调用方在窗口上屏后隔一段时间再压两次，防止系统栏被系统重新显示。
     */
    private fun applyImmersive() {
        val w = window ?: return
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            w.setDecorFitsSystemWindows(false)
            w.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            w.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }

    // ------------------------------------------------------------ 手势传感器

    /**
     * 按 framework-res 的配置注册 doze 手势传感器。
     *
     * framework 只给三个「传感器类型」字符串：
     * `config_dozeTapSensorType` / `config_dozeDoubleTapSensorType` /
     * `config_dozeLongPressSensorType`，SystemUI 的 `DozeSensors` 就是拿它们去
     * `SensorManager` 里按 `Sensor.getStringType()` 匹配。我们自己当 doze 组件，
     * 这套注册逻辑也得自己来，否则那三个配置形同虚设。
     *
     * 匹配不到（虚拟机、没有对应硬件的机型）就静默跳过，不影响触摸唤醒。
     */
    private fun registerDozeSensors() {
        val manager = getSystemService(SensorManager::class.java) ?: return
        unregisterDozeSensors()

        val types = listOf(
            SENSOR_TAP,
            SENSOR_DOUBLE_TAP,
            SENSOR_LONG_PRESS,
        ).map { readFrameworkString(it) }
            .filter { it.isNotBlank() }
            .distinct()

        if (types.isEmpty()) {
            Log.i(TAG, "no config_doze*SensorType configured")
            return
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // 厂商实现的 tap 传感器一般用 values[0] 表示敲击次数/是否触发，
                // 0 视为「无事件」，避免一次敲击被回调两次
                if (event.values.isNotEmpty() && event.values[0] == 0f) return
                Log.i(TAG, "gesture ${event.sensor.stringType} -> wake")
                wakeToLockScreen()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorListener = listener

        var registered = 0
        for (type in types) {
            val sensor = manager.getSensorList(Sensor.TYPE_ALL)
                .filter { it.stringType == type }
                // 同一 type 有 wake-up / 非 wake-up 两个版本时优先 wake-up：
                // AP 休眠后只有 wake-up 传感器还能把事件送上来
                .maxByOrNull { if (it.isWakeUpSensor) 1 else 0 }
                ?: continue
            val ok = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            if (ok) registered++
            Log.i(TAG, "sensor $type -> ${sensor.name} wakeUp=${sensor.isWakeUpSensor} ok=$ok")
        }

        if (registered == 0) Log.i(TAG, "no matching doze sensor on this device: $types")
    }

    private fun unregisterDozeSensors() {
        val listener = sensorListener ?: return
        getSystemService(SensorManager::class.java)?.unregisterListener(listener)
        sensorListener = null
    }

    /** 读 framework-res 里的字符串资源（config_doze* 不是 public API，只能按名字查） */
    private fun readFrameworkString(name: String): String {
        val id = resources.getIdentifier(name, "string", "android")
        if (id == 0) return ""
        return runCatching { resources.getString(id) }.getOrDefault("")
    }

    override fun onDreamingStopped() {
        Log.i(TAG, "onDreamingStopped")
        unregisterDozeSensors()
        handler.removeCallbacks(ticker)
        handler.removeCallbacksAndMessages(null)
        stage = null
        column = null
        dateView = null
        batteryView = null
        hintView = null
        waking = false
        super.onDreamingStopped()
    }

    // ------------------------------------------------------------ doze 面板

    /**
     * 请求进入 doze 面板。
     *
     * 顺序不能反：`startDozing()` 把 dream 标记为 dozing 之后，
     * `setDozeScreenState()` / `setDozeScreenBrightness()` 才会被下发到
     * DisplayManager，否则这两个调用会被静默丢弃。
     */
    private fun enterDozePanel() {
        val started = callDream("startDozing")
        val stateApplied = callDream("setDozeScreenState", Display.STATE_DOZE)
        // 亮度是 1..255 的绝对亮度；AOD 取低值，既省电又不至于看不见
        callDream("setDozeScreenBrightness", DOZE_BRIGHTNESS)
        Log.i(TAG, "enterDozePanel started=$started stateApplied=$stateApplied")
    }

    /**
     * 回读主屏状态，确认 DOZE 是否真的生效。
     *
     * 虚拟机 / 部分 LCD 机型没有 DOZE 面板，`setDozeScreenState(STATE_DOZE)`
     * 会被忽略甚至直接关屏；这种情况下回到 `STATE_ON`，让窗口正常显示，
     * 功能退化成「常亮 AOD」，但至少不是黑屏。
     */
    private fun verifyPanel() {
        val state = runCatching {
            getSystemService(DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY)
                ?.state
        }.getOrNull() ?: Display.STATE_UNKNOWN

        Log.i(TAG, "display state after doze request = ${stateName(state)}")

        if (state == Display.STATE_DOZE || state == Display.STATE_DOZE_SUSPEND) {
            // 记住这台机器能进 DOZE，下次熄屏不用再试探
            prefs.edit().putBoolean(KEY_DOZE_PANEL, true).apply()
            return
        }

        Log.w(TAG, "DOZE panel unsupported here, fallback to STATE_ON")
        prefs.edit().putBoolean(KEY_DOZE_PANEL, false).apply()
        callDream("setDozeScreenState", Display.STATE_ON)
        setScreenBright(true)

        // 试探的那一下把屏幕关黑过，刚播的入场动画被吃掉了；
        // 回退到常亮后补播一次，第一次用这台设备也能看到完整效果
        stage?.post { playEnterAnimation() }
    }

    /** 已经确认没有 DOZE 面板的机器，后续直接常亮显示，不再黑一下 */
    private fun dozePanelSupported(): Boolean =
        prefs.getBoolean(KEY_DOZE_PANEL, true)

    /**
     * 反射调用 DreamService 上的 @hide 方法。
     *
     * 这些方法（`startDozing` / `setDozeScreenState` / `setDozeScreenBrightness`）
     * 属于 @SystemApi，公开 SDK 里没有，直接用会编译不过；
     * 反射的另一个好处是方法缺失时只会让 AOD 降级，而不是整个服务崩溃。
     */
    private fun callDream(name: String, vararg args: Int): Boolean {
        return try {
            val types = Array(args.size) { Int::class.javaPrimitiveType!! }
            DreamService::class.java.getMethod(name, *types).invoke(this, *args.toTypedArray())
            true
        } catch (t: Throwable) {
            Log.w(TAG, "call $name failed: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    /** 读 DreamService 上的 @hide 布尔属性，缺失时返回 null */
    private fun readBool(name: String): Boolean? = runCatching {
        DreamService::class.java.getMethod(name).invoke(this) as? Boolean
    }.getOrNull()

    private fun stateName(state: Int): String = when (state) {
        Display.STATE_OFF -> "OFF"
        Display.STATE_ON -> "ON"
        Display.STATE_DOZE -> "DOZE"
        Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
        Display.STATE_ON_SUSPEND -> "ON_SUSPEND"
        Display.STATE_VR -> "VR"
        Display.STATE_UNKNOWN -> "UNKNOWN"
        else -> state.toString()
    }

    // ---------------------------------------------------------------- UI

    private fun buildContentView(): ViewGroup {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            // 熄屏显示全程只用纯黑底，OLED 上不点亮的像素不耗电
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    wakeToLockScreen()
                }
                true
            }
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        this.column = column

        val clock = TextClock(this).apply {
            // 跟随系统 12/24 小时制
            setFormat12Hour("h:mm")
            setFormat24Hour("HH:mm")
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 92f)
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            includeFontPadding = false
            letterSpacing = 0.02f
        }
        column.addView(clock, wrapParams(top = 0))

        val date = TextView(this).apply {
            setTextColor(DATE_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            letterSpacing = 0.08f
        }
        dateView = date
        column.addView(date, wrapParams(top = 8))

        val battery = TextView(this).apply {
            setTextColor(BATTERY_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        batteryView = battery
        column.addView(battery, wrapParams(top = 18))

        // 单独一层承载进场动画：整层淡入 + 轻微上浮 + 由小放大，
        // 里面的 column 仍然只负责防烧屏位移，两套变换互不干扰
        val stage = FrameLayout(this).also { this.stage = it }
        stage.addView(
            column,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        root.addView(
            stage,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val hint = TextView(this).apply {
            setText(R.string.aod_hint_tap_unlock)
            setTextColor(HINT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        hintView = hint
        root.addView(
            hint,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply { bottomMargin = dp(56) },
        )

        return root
    }

    private fun wrapParams(top: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(top) }

    /** 日期、电量、防烧屏位移：每分钟一次 */
    private fun refreshSecondary() {
        val now = Date()
        val locale = Locale.getDefault()
        val pattern = DateFormat.getBestDateTimePattern(locale, "MMMMdEEEE")
        dateView?.text = SimpleDateFormat(pattern, locale).format(now)

        batteryView?.text = batteryText()
        // 像素位移：OLED 长时间显示同一画面会烧屏，整块内容按分钟在几个位置间轮换
        val step = (System.currentTimeMillis() / 60_000L % BURN_IN_STEPS).toInt()
        val index = step - BURN_IN_STEPS / 2
        column?.apply {
            translationX = dp(index * 3).toFloat()
            translationY = dp(index * 2).toFloat()
        }
    }

    private fun batteryText(): String {
        val manager = getSystemService(BatteryManager::class.java)
        val percent = manager
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.coerceIn(0, 100) ?: 0
        val charging = manager?.isCharging == true
        return if (charging) {
            getString(R.string.aod_battery_charging, percent)
        } else {
            getString(R.string.aod_battery, percent)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    // ------------------------------------------------------------ 交互

    /**
     * 轻触唤醒到锁屏。
     *
     * `PowerManager.wakeUp()` 是 @SystemApi，公开 SDK 里没有这个方法；
     * 而 `FULL_WAKE_LOCK` 在 PowerManagerService 内部走的正是
     * `wakeUpNoUpdateLocked()`，从 doze 唤醒到锁屏的效果一致，且只需普通权限。
     * 超时后自动释放，不会留下常亮。
     */
    private fun wakeToLockScreen() {
        if (waking) return
        waking = true
        Log.i(TAG, "tap to wake")

        // 1) 先把面板拉回 ON。否则 finish() 之后显示状态还停在 DOZE，
        //    系统会直接把屏幕关掉 —— 表现就是「亮一下马上黑」。
        callDream("setDozeScreenState", Display.STATE_ON)

        // 2) 真正唤醒设备。wakeUp() 是 @SystemApi，需要 DEVICE_POWER，
        //    只有平台签名 / priv-app 才拿得到；拿不到就走下面的兜底。
        val woke = tryWakeUp()

        // 3) 兜底：ACQUIRE_CAUSES_WAKEUP 才是「把设备从 sleep 里拉起来」的开关。
        //    只挂 FULL_WAKE_LOCK 等于「要求常亮」，但设备仍然是睡着的，
        //    锁一释放立刻重新熄屏 —— 之前 1 秒的锁就是这么黑的。
        holdScreenOn(if (woke) WAKE_HOLD_MS else FALLBACK_HOLD_MS)

        finish()
    }

    /**
     * 反射调用 `PowerManager.wakeUp()`。
     *
     * 先试新签名 `wakeUp(long, int, String)`，再退回老的 `wakeUp(long)`；
     * 两种都失败（多半是缺 DEVICE_POWER）就返回 false，由 wakelock 兜底。
     */
    private fun tryWakeUp(): Boolean {
        val power = getSystemService(PowerManager::class.java) ?: return false
        val now = SystemClock.uptimeMillis()
        return try {
            PowerManager::class.java
                .getMethod(
                    "wakeUp",
                    Long::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                )
                .invoke(power, now, 0, WAKE_REASON)
            Log.i(TAG, "wakeUp() ok")
            true
        } catch (t3: Throwable) {
            try {
                PowerManager::class.java
                    .getMethod("wakeUp", Long::class.javaPrimitiveType)
                    .invoke(power, now)
                Log.i(TAG, "wakeUp(long) ok")
                true
            } catch (t1: Throwable) {
                Log.w(TAG, "wakeUp unavailable: ${t1.javaClass.simpleName}: ${t1.message}")
                false
            }
        }
    }

    /** 顶一段常亮，给系统时间把锁屏拉起来 */
    private fun holdScreenOn(timeoutMs: Long) {
        try {
            val power = getSystemService(PowerManager::class.java)
            @Suppress("DEPRECATION")
            val lock = power.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                WAKE_LOCK_TAG,
            )
            lock.setReferenceCounted(false)
            lock.acquire(timeoutMs)
        } catch (t: Throwable) {
            Log.w(TAG, "hold screen on failed", t)
        }
    }

    private companion object {
        const val TAG = "AodDozeService"
        const val WAKE_LOCK_TAG = "ty:aod-tap"
        const val WAKE_REASON = "com.android.ty.systemservices:aod-tap"
        /** wakeUp() 成功时设备已经 interactive，这段时间只是保险 */
        const val WAKE_HOLD_MS = 4_000L
        /** 没拿到 DEVICE_POWER 时的兜底常亮，太短会被当成「亮一下又黑」 */
        const val FALLBACK_HOLD_MS = 10_000L
        /** 防烧屏的位置个数（奇数，保证有一个居中位） */
        const val BURN_IN_STEPS = 5
        /** 首帧提交后多久请求切 doze 面板（要等进场动画播完） */
        const val DOZE_ENTER_DELAY_MS = 1_050L
        /** 切面板后多久回读显示状态做兜底判断（太长会让不支持的设备多黑一会儿） */
        const val DOZE_VERIFY_DELAY_MS = 1_750L
        /** AOD 面板亮度（1..255），太低在 LCD 上几乎看不见 */
        const val DOZE_BRIGHTNESS = 90
        /**
         * 进场动画时长与起始位移。
         * 900ms / 10dp 对齐的是 SystemUI 的 doze 过渡：缓慢、线性、位移极小，
         * 观感是「从黑里浮出来」而不是「弹出来」。
         */
        const val ENTER_DURATION_MS = 900L
        const val ENTER_OFFSET_DP = 10
        /** DOZE 面板能力缓存（device-protected） */
        const val PREFS_NAME = "aod_state"
        const val KEY_DOZE_PANEL = "doze_panel_supported"
        /** framework-res 里三个 doze 手势传感器类型配置名 */
        const val SENSOR_TAP = "config_dozeTapSensorType"
        const val SENSOR_DOUBLE_TAP = "config_dozeDoubleTapSensorType"
        const val SENSOR_LONG_PRESS = "config_dozeLongPressSensorType"
        val DATE_COLOR = Color.parseColor("#B3FFFFFF")
        val BATTERY_COLOR = Color.parseColor("#80FFFFFF")
        val HINT_COLOR = Color.parseColor("#4DFFFFFF")
    }
}
