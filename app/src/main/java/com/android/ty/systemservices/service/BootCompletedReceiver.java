package com.android.ty.systemservices.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机后重放「禁止下拉状态栏」标志。
 *
 * <p>StatusBarManager 的 disable 标志只保存在 system_server 内存中，
 * 重启后会被清空，因此必须在这里按持久化的开关重新下发。</p>
 */
public class BootCompletedReceiver extends BroadcastReceiver {

    private static final String TAG = "BootCompletedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        Log.i(TAG, "reapply status bar lock on " + action);
        StatusBarController.reapplyIfEnabled(context);
    }
}
