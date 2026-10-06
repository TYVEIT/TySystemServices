package com.android.ty.systemservices.Overlay;

import android.os.Bundle;

import com.android.ty.systemservices.CollapsingToolbarBaseActivity;
import com.android.ty.systemservices.SettingsActivity;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.ListPreference;

import com.android.ty.systemservices.R;

public class OverlayActivity extends CollapsingToolbarBaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_overlay);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content_frame, new OverlayPreferenceFragment())
                .commit();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
    public static class OverlayPreferenceFragment extends PreferenceFragmentCompat {

        private OverlayCategoryPreferenceController[] mControllers;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            // 与 development_settings.xml 里 theme_customization_category 一致的三项
            String[] categories = {
                    "android.theme.customization.accent_color",
                    "android.theme.customization.font",
                    "android.theme.customization.adaptive_icon_shape"
            };
            // 加载 XML：三个 ListPreference 只有 key + title，entries 运行时注入
            setPreferencesFromResource(R.xml.overlay_preferences, rootKey);

            mControllers = new OverlayCategoryPreferenceController[categories.length];
            PreferenceScreen screen = getPreferenceScreen();
            for (int i = 0; i < categories.length; i++) {
                OverlayCategoryPreferenceController c =
                        new OverlayCategoryPreferenceController(requireContext(), categories[i]);
                mControllers[i] = c;
                // 对应 displayPreference(screen) + setOnPreferenceChangeListener
                c.displayPreference(screen);
                // 动态填充 entries/entryValues/当前值
                c.updateState();
            }
        }
    }
}