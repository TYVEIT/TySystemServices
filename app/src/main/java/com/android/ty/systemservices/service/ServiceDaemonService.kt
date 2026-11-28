package com.android.ty.systemservices.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * 悬浮窗守护服务 —— **由 system_server 直接启动**。
 *
 * 启动链路：
 * ```
 * system_server (OverlayBoot)
 *   └─ startServiceAsUser(ServiceDaemonService)
 *        └─ onCreate / onStartCommand → TestModeOverlay.restore()
 *             └─ WindowManager.addView(2003 / 2010 / 2015 依次回退)
 * ```
 *
 * 自身也承担应用内守护：
 * - `START_STICKY`：进程被系统回收后由 AMS 重新拉起；
 * - `onTaskRemoved` / `onDestroy`：补排一次闹钟，避免被一次性杀掉后再也起不来；
 * - 开关关闭时立即 `stopSelf()`，不留常驻进程。
 */
class ServiceDaemonService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "daemon service created")
        sync()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sync()
        return START_STICKY
    }

    /** 统一入口：开关开着就恢复浮层并续订闹钟，关着就自己退出 */
    private fun sync() {
        // system_server 心跳可能绕过 ServiceDaemon.start 直接拉起本服务，
        // 这里再检测一次开机向导状态，保证两条链路行为一致
        ServiceDaemon.launchSetupWizardIfNeeded(this)

        if (!TestModeOverlay.isEnabled(this)) {
            Log.i(TAG, "overlay disabled, stopping daemon")
            stopSelf()
            return
        }
        TestModeOverlay.restore(this)
        // 处于开机向导倒计时窗口时排 5s 密集采样，其余时间回到 5 分钟心跳
        ServiceDaemon.schedule(this, ServiceDaemon.nextWakeDelayMs(this))
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务划掉应用时，尽快唤醒一次（服务本身可能随后被停）
        if (TestModeOverlay.isEnabled(this)) {
            ServiceDaemon.schedule(this, 1_500L)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // 被系统杀掉/主动停止时补一次闹钟；开关已关就不再排，避免无限拉起
        if (TestModeOverlay.isEnabled(this)) {
            ServiceDaemon.schedule(this, 5_000L)
        }
        Log.i(TAG, "daemon service destroyed")
        super.onDestroy()
    }

    private companion object {
        const val TAG = "ServiceDaemonService"
    }
}
