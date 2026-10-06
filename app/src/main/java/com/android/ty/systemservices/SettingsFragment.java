package com.android.ty.systemservices;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import androidx.preference.TwoStatePreference;

import com.android.ty.systemservices.Overlay.OverlayActivity;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SettingsFragment extends PreferenceFragmentCompat {
    private static final String KEY_STATUS_BAR_QS = "status_bar_qs";
    private static final String KEY_NVG_HOME = "nvg_home";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey);

        Preference enableSetup = findPreference("enable_setup");
        if (enableSetup != null) {
            enableSetup.setOnPreferenceClickListener(pref -> {
                enablePackage("com.android.setup");
                return true;
            });
        }
        Preference openoverlay = findPreference("overlay_open_preference");
        if (openoverlay != null) {
            openoverlay.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(getActivity(), OverlayActivity.class);
                startActivity(intent);
                return true;
            });
        }

        bindGlobalSwitch(KEY_STATUS_BAR_QS);
        bindGlobalSwitch(KEY_NVG_HOME);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 同步当前 Settings.Global 值到开关，处理外部修改的情况
        syncGlobalSwitch(KEY_STATUS_BAR_QS);
        syncGlobalSwitch(KEY_NVG_HOME);
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
            boolean value = (Boolean) newValue;
            try {
                boolean ok = Settings.Global.putInt(
                        getContext().getContentResolver(), key, value ? 1 : 0);
                if (ok) {
                    String state = value ? "开启 (true)" : "关闭 (false)";
                    Toast.makeText(getContext(), key + " " + state,
                            Toast.LENGTH_SHORT).show();
                    return true;  // 允许开关切换
                } else {
                    Toast.makeText(getContext(), key + " 设置失败",
                            Toast.LENGTH_SHORT).show();
                    return false;  // 写入失败，保持原状态
                }
            } catch (Exception e) {
                Toast.makeText(getContext(), key + " 设置失败: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                return false;
            }
        });
    }

    /** 从 Settings.Global 读取当前值并同步到开关显示 */
    private void syncGlobalSwitch(String key) {
        TwoStatePreference pref = findPreference(key);
        if (pref == null) return;
        try {
            int v = Settings.Global.getInt(getContext().getContentResolver(), key, 0);
            pref.setChecked(v == 1);
        } catch (Exception e) {
            pref.setChecked(false);
        }
    }

    private void enablePackage(String packageName) {
        try {
            getContext().getPackageManager().setApplicationEnabledSetting(
                    packageName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    0);
            Toast.makeText(getContext(), "已启用 " + packageName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "启用失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}