package com.android.ty.systemservices.service;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 通过反射调用系统隐藏 API {@code android.app.StatusBarManager}，
 * 实现「禁止下拉状态栏（通知栏 / 快捷设置）」。
 *
 * <p>适用前提：应用使用平台签名并声明 {@code android:sharedUserId="android.uid.system"}，
 * 否则 {@code disable()} 会抛 {@link SecurityException}。</p>
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>只调用 {@code StatusBarManager.disable(int)}，这是 AOSP 中 DevicePolicyManager
 *       {@code setStatusBarDisabled} 内部走的同一条链路，兼容性最好；</li>
 *   <li>Android 12+ 额外下发 {@code DISABLE2_QUICK_SETTINGS | DISABLE2_NOTIFICATION_SHADE}
 *       作为兜底，常量通过反射读取，避免不同版本常量值漂移（4 在旧版本是
 *       DISABLE2_GLOBAL_ACTIONS，硬编码会误关电源菜单）；</li>
 *   <li>disable 标志只存在于 system_server 内存，重启后丢失，因此把开关状态写入
 *       {@link Settings.Global}，由 {@link BootCompletedReceiver} 在开机后重新下发。</li>
 * </ul>
 */
public final class StatusBarController {

    private static final String TAG = "StatusBarController";

    /** 与 settings_preferences.xml 中的 key 保持一致 */
    public static final String KEY_STOP_STATUS = "stop_status";

    private static final String STATUS_BAR_SERVICE = "statusbar";
    private static final String PERMISSION_STATUS_BAR = "android.permission.STATUS_BAR";

    private static final Object LOCK = new Object();

    /** StatusBarManager.DISABLE_EXPAND 的默认值，反射取不到时兜底 */
    private static final int DEFAULT_DISABLE_EXPAND = 0x00010000;

    private static Object sStatusBarManager;
    private static Method sDisable;
    private static Method sDisable2;
    private static Method sCollapse;
    private static int sDisableExpand = DEFAULT_DISABLE_EXPAND;
    private static int sDisable2Shade;
    private static boolean sResolved;

    private StatusBarController() {
    }

    /** 操作结果，带失败原因，方便 UI 直接提示 */
    public static final class Result {
        public final boolean success;
        public final String reason;

        private Result(boolean success, String reason) {
            this.success = success;
            this.reason = reason;
        }

        static Result ok(String reason) {
            return new Result(true, reason);
        }

        static Result fail(String reason) {
            return new Result(false, reason);
        }
    }

    /**
     * 开关入口：下发系统标志 + 持久化。
     *
     * <p>先下发再持久化，持久化失败时回滚系统标志，避免出现
     * 「界面上开了、重启后失效」的假成功。</p>
     */
    public static Result setEnabled(Context context, boolean enabled) {
        if (context == null) {
            return Result.fail("context 为空");
        }
        Context app = context.getApplicationContext();
        if (app == null) {
            app = context;
        }
        synchronized (LOCK) {
            Result applied = applyFlags(app, enabled);
            if (!applied.success) {
                return applied;
            }
            try {
                boolean saved = Settings.Global.putInt(
                        app.getContentResolver(), KEY_STOP_STATUS, enabled ? 1 : 0);
                if (!saved) {
                    applyFlags(app, !enabled);
                    return Result.fail("写入 Settings.Global 失败");
                }
            } catch (Throwable t) {
                applyFlags(app, !enabled);
                Log.e(TAG, "persist " + KEY_STOP_STATUS + " failed", t);
                return Result.fail("写入 Settings.Global 失败：" + t.getMessage());
            }
            return Result.ok(enabled ? "已禁止下拉状态栏" : "已恢复下拉状态栏");
        }
    }

    /** 读取持久化的开关状态（真正的系统标志没有公开 getter，只能以该值为准） */
    public static boolean isEnabled(Context context) {
        if (context == null) {
            return false;
        }
        try {
            return Settings.Global.getInt(
                    context.getContentResolver(), KEY_STOP_STATUS, 0) == 1;
        } catch (Throwable t) {
            Log.w(TAG, "read " + KEY_STOP_STATUS + " failed", t);
            return false;
        }
    }

    /**
     * 开机 / 应用进程启动时调用：若开关为开，则把标志重新下发。
     * disable 标志保存在 system_server 内存中，重启后会丢失，必须重放。
     */
    public static void reapplyIfEnabled(Context context) {
        if (context == null || !isEnabled(context)) {
            return;
        }
        Context app = context.getApplicationContext();
        if (app == null) {
            app = context;
        }
        synchronized (LOCK) {
            Result r = applyFlags(app, true);
            if (!r.success) {
                Log.w(TAG, "reapply failed: " + r.reason);
            }
        }
    }

    /** 只下发系统标志，不写 Settings */
    private static Result applyFlags(Context app, boolean disabled) {
        Object sbm = getStatusBarManager(app);
        if (sbm == null) {
            return Result.fail("获取 StatusBarManager 失败");
        }
        resolveHiddenApi();
        if (sDisable == null) {
            return Result.fail("反射找不到 StatusBarManager.disable(int)，请确认系统版本与签名");
        }

        // 打开开关时先把已经展开的通知栏收起，否则会停在「打开但关不掉」的状态
        if (disabled && sCollapse != null) {
            try {
                sCollapse.invoke(sbm);
            } catch (Throwable t) {
                Log.w(TAG, "collapsePanels failed", t);
            }
        }

        int flags = disabled ? sDisableExpand : 0;
        try {
            sDisable.invoke(sbm, flags);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            Log.e(TAG, "StatusBarManager.disable(" + flags + ") failed", cause);
            if (cause instanceof SecurityException) {
                return Result.fail("缺少 " + PERMISSION_STATUS_BAR
                        + " 权限（需要平台签名，当前 uid=" + Process.myUid() + "）");
            }
            return Result.fail("调用 disable() 失败：" + cause);
        } catch (Throwable t) {
            Log.e(TAG, "StatusBarManager.disable(" + flags + ") failed", t);
            return Result.fail("调用 disable() 失败：" + t);
        }

        // 12+ 的兜底：禁用快捷设置面板 + 通知栏
        if (sDisable2Shade != 0 && sDisable2 != null) {
            try {
                sDisable2.invoke(sbm, disabled ? sDisable2Shade : 0);
            } catch (Throwable t) {
                // 兜底失败不影响主流程，DISABLE_EXPAND 已经生效
                Log.w(TAG, "disable2 failed", t);
            }
        }
        return Result.ok(disabled ? "已禁止下拉状态栏" : "已恢复下拉状态栏");
    }

    private static Object getStatusBarManager(Context app) {
        if (sStatusBarManager != null) {
            return sStatusBarManager;
        }
        try {
            sStatusBarManager = app.getSystemService(STATUS_BAR_SERVICE);
        } catch (Throwable t) {
            Log.e(TAG, "getSystemService(statusbar) failed", t);
        }
        return sStatusBarManager;
    }

    /**
     * 解析隐藏 API。方法名固定，参数类型必须用 int.class 而不是 Integer.class，
     * 否则会抛 NoSuchMethodException。
     */
    private static void resolveHiddenApi() {
        if (sResolved) {
            return;
        }
        sResolved = true;
        try {
            Class<?> cls = Class.forName("android.app.StatusBarManager");
            sDisable = cls.getMethod("disable", int.class);
            sDisable2 = cls.getMethod("disable2", int.class);
            sCollapse = cls.getMethod("collapsePanels");
            sDisableExpand = readStaticInt(cls, "DISABLE_EXPAND", DEFAULT_DISABLE_EXPAND);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // 常量按版本会变（旧版本 4 是 DISABLE2_GLOBAL_ACTIONS），必须读真实值
                sDisable2Shade = readStaticInt(cls, "DISABLE2_QUICK_SETTINGS", 0)
                        | readStaticInt(cls, "DISABLE2_NOTIFICATION_SHADE", 0);
            }
        } catch (Throwable t) {
            Log.e(TAG, "resolve hidden api failed", t);
            sDisable = null;
        }
    }

    private static int readStaticInt(Class<?> cls, String field, int defValue) {
        try {
            return cls.getField(field).getInt(null);
        } catch (Throwable t) {
            Log.w(TAG, "read " + field + " failed, use " + defValue, t);
            return defValue;
        }
    }
}
