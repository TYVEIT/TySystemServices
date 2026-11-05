package com.android.ty.systemservices.aod

import android.graphics.Color
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.dreams.DozeService
import android.text.format.DateFormat
import android.util.Log
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
 * 每次灭屏进入 doze 都会绑定这里，由它负责在低功耗面板（DOZE 态）上作画，
 * 取代默认的 `com.android.systemui/.doze.DozeService`。
 *
 * 关键约束：
 * - 必须在 manifest 里声明 `BIND_DREAM_SERVICE`（signature 级）与 DreamService 的 intent-filter，
 *   否则 DreamManagerService 拒绝绑定；
 * - 必须调用 [setDozeScreenState] 把面板拉到 `STATE_DOZE`，否则面板保持断电，画什么都看不见；
 * - 这里的 UI 用原生 View 而不是 Compose：doze 窗口不是 Activity，没有
 *   `ViewTreeLifecycleOwner`，Compose 需要额外手工挂 owner，一旦出问题就是灭屏后全黑，
 *   而 AOD 是每次熄屏都会走到的路径，不适合冒这个险。
 *
 * 交互：轻触屏幕 -> 唤醒到锁屏。
 */
class AodDozeService : DozeService() {

    private val handler = Handler(Looper.getMainLooper())
    private var column: LinearLayout? = null
    private var dateView: TextView? = null
    private var batteryView: TextView? = null

    /** 每分钟刷新日期 / 电量 / 防烧屏偏移 */
    private val ticker = object : Runnable {
        override fun run() {
            refreshSecondary()
            handler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // 触摸由 AOD 自己消费，实现「轻触解锁」
        setInteractive(true)
        setFullscreen(true)
        // 亮度交给 doze 面板控制，不强制拉满
        setScreenBright(false)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()

        // 面板进入低功耗 DOZE 态（不是全关），这是能看见画面的大前提
        try {
            setDozeScreenState(Display.STATE_DOZE)
        } catch (t: Throwable) {
            Log.w(TAG, "setDozeScreenState failed", t)
        }

        setContentView(buildContentView())

        refreshSecondary()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(ticker)
        column = null
        dateView = null
        batteryView = null
        super.onDreamingStopped()
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

        root.addView(
            column,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )

        val hint = TextView(this).apply {
            setText(R.string.aod_hint_tap_unlock)
            setTextColor(HINT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
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
        try {
            val power = getSystemService(PowerManager::class.java)
            @Suppress("DEPRECATION")
            val lock = power.newWakeLock(PowerManager.FULL_WAKE_LOCK, WAKE_LOCK_TAG)
            lock.setReferenceCounted(false)
            lock.acquire(1_000L)
        } catch (t: Throwable) {
            Log.w(TAG, "wake up failed", t)
        }
        finish()
    }

    private companion object {
        const val TAG = "AodDozeService"
        const val WAKE_LOCK_TAG = "ty:aod-tap"
        /** 防烧屏的位置个数（奇数，保证有一个居中位） */
        const val BURN_IN_STEPS = 5
        val DATE_COLOR = Color.parseColor("#B3FFFFFF")
        val BATTERY_COLOR = Color.parseColor("#80FFFFFF")
        val HINT_COLOR = Color.parseColor("#4DFFFFFF")
    }
}
