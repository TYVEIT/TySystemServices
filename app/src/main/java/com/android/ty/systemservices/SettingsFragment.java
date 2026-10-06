package com.android.ty.systemservices;

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

        Preference statusBarQs = findPreference(KEY_STATUS_BAR_QS);
        if (statusBarQs != null) {
            statusBarQs.setOnPreferenceClickListener(pref -> {
                toggleGlobalSetting(KEY_STATUS_BAR_QS);
                return true;
            });
        }

        Preference nvgHome = findPreference(KEY_NVG_HOME);
        if (nvgHome != null) {
            nvgHome.setOnPreferenceClickListener(pref -> {
                toggleGlobalSetting(KEY_NVG_HOME);
                return true;
            });
        }
    }

    /** 切换 Settings.Global 中对应 key 的布尔值：开启=true，关闭=false */
    private void toggleGlobalSetting(String key) {
        try {
            int current = Settings.Global.getInt(
                    getContext().getContentResolver(), key, 0);
            int newValue = (current == 1) ? 0 : 1;
            boolean ok = Settings.Global.putInt(
                    getContext().getContentResolver(), key, newValue);
            if (ok) {
                String state = (newValue == 1) ? "开启 (true)" : "关闭 (false)";
                Toast.makeText(getContext(), key + " " + state, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), key + " 设置失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), key + " 设置失败: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
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