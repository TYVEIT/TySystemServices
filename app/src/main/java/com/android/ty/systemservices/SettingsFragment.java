package com.android.ty.systemservices;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.TwoStatePreference;

import com.android.ty.systemservices.Overlay.OverlayActivity;
import com.android.ty.systemservices.compose.MingActivity;
import com.android.ty.systemservices.service.StatusBarController;

public class SettingsFragment extends PreferenceFragmentCompat {
    private static final String TAG = "SettingsFragment";

    private static final String KEY_STATUS_BAR_QS = "status_bar_qs";
    private static final String KEY_NVG_HOME = "nvg_home";
    /** 禁止下拉状态栏，key 与 settings_preferences.xml 保持一致 */
    private static final String KEY_STOP_STATUS = StatusBarController.KEY_STOP_STATUS;
    /** 音频控制：写 Settings.Global 的 audio_control（开 = 1，关 = 0） */
    private static final String KEY_AUDIO_CONTROL = "audio_control";

    /**
     * 使用 Application Context，避免 Fragment 已 detach 时 getContext() 返回 null
     * 导致回调里 NPE（原实现在 onPreferenceChange 里直接 getContext()）。
     */
    private Context appContext;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey);
        appContext = requireContext().getApplicationContext();

        Preference enableSetup = findPreference("enable_setup");
        if (enableSetup != null) {
            enableSetup.setOnPreferenceClickListener(pref -> {
                enablePackage("com.android.setup");
                return true;
            });
        }
        Preference opentysett = findPreference("open_tysettings");
        Preference openoverlay = findPreference("overlay_open_preference");
        if (openoverlay != null) {
            openoverlay.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(getActivity(), OverlayActivity.class);
                startActivity(intent);
                return true;
            });
        }
        if (opentysett != null) {
            opentysett.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(getActivity(), MingActivity.class);
                startActivity(intent);
                return true;
            });
        }

        bindGlobalSwitch(KEY_STATUS_BAR_QS);
        bindGlobalSwitch(KEY_NVG_HOME);
        bindGlobalSwitch(KEY_AUDIO_CONTROL);
        bindStopStatusSwitch();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 同步当前 Settings.Global 值到开关，处理外部修改的情况
        syncGlobalSwitch(KEY_STATUS_BAR_QS);
        syncGlobalSwitch(KEY_NVG_HOME);
        syncGlobalSwitch(KEY_AUDIO_CONTROL);

        // disable 标志可能被系统重启清空，进页面时按持久化状态补一次，保证开关与实际一致
        StatusBarController.reapplyIfEnabled(appContext);
        syncStopStatusSwitch();
    }

    /**
     * 把 ComposeSwitchPreference 绑定到 Settings.Global：
     * 开启 -> true (写入 1)，关闭 -> false (写入 0)
     */
    private void bindGlobalSwitch(String key) {
        TwoStatePreference pref = findPreference(key);
        if (pref == null) return;
        // 状态以 Settings.Global 为准，不让 Preference 框架自行持久化到 SharedPreferences
        pref.setPersistent(false);
        pref.setOnPreferenceChangeListener((p, newValue) -> {
            boolean value = Boolean.TRUE.equals(newValue);
            String label = labelOf(p, key);
            try {
                boolean ok = Settings.Global.putInt(
                        appContext.getContentResolver(), key, value ? 1 : 0);
                if (ok) {
                    toast(label + (value ? " 开启" : " 关闭"));
                    return true;  // 允许开关切换
                }
                toast(label + " 设置失败");
                return false;  // 写入失败，保持原状态
            } catch (Exception e) {
                Log.e(TAG, "write Settings.Global " + key + " failed", e);
                toast(label + " 设置失败: " + e.getMessage());
                return false;
            }
        });
    }

    /** 提示文案优先用条目标题，避免把英文 key 直接抛给用户 */
    private static String labelOf(Preference pref, String fallbackKey) {
        CharSequence title = pref.getTitle();
        return title == null || title.length() == 0 ? fallbackKey : title.toString();
    }

    /**
     * 禁止下拉状态栏：通过反射调用 StatusBarManager.disable(DISABLE_EXPAND)。
     * 失败时返回 false，开关自动保持原状态。
     */
    private void bindStopStatusSwitch() {
        TwoStatePreference pref = findPreference(KEY_STOP_STATUS);
        if (pref == null) return;
        pref.setPersistent(false);
        syncStopStatusSwitch();
        pref.setOnPreferenceChangeListener((p, newValue) -> {
            boolean enable = Boolean.TRUE.equals(newValue);
            StatusBarController.Result result = StatusBarController.setEnabled(appContext, enable);
            toast(result.reason);
            return result.success;
        });
    }

    /** 从 Settings.Global 读取当前值并同步到开关显示 */
    private void syncGlobalSwitch(String key) {
        TwoStatePreference pref = findPreference(key);
        if (pref == null) return;
        try {
            int v = Settings.Global.getInt(appContext.getContentResolver(), key, 0);
            pref.setChecked(v == 1);
        } catch (Exception e) {
            Log.e(TAG, "read Settings.Global " + key + " failed", e);
            pref.setChecked(false);
        }
    }

    private void syncStopStatusSwitch() {
        TwoStatePreference pref = findPreference(KEY_STOP_STATUS);
        if (pref == null) return;
        pref.setChecked(StatusBarController.isEnabled(appContext));
    }

    private void enablePackage(String packageName) {
        try {
            appContext.getPackageManager().setApplicationEnabledSetting(
                    packageName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    0);
            toast("已启用 " + packageName);
        } catch (Exception e) {
            Log.e(TAG, "enable " + packageName + " failed", e);
            toast("启用失败: " + e.getMessage());
        }
    }

    private void toast(String message) {
        if (message == null || getContext() == null) return;
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }
}
