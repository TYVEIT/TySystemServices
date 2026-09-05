package com.android.ty.systemservices;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsFragment extends PreferenceFragmentCompat {

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