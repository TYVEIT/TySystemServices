package com.example.ia_demo;

import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * Minimal PreferenceActivity loaded from res/xml/pref_settings.xml.
 * Reached from the system Settings homepage via the IA activity-alias
 * declared in AndroidManifest.xml (TopLevelSettingsActivity).
 */
public class SettingsActivity extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_settings);
    }
}
