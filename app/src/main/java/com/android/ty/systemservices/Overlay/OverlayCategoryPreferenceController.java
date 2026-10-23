package com.android.ty.systemservices.Overlay;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.ty.systemservices.R;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 忠实复刻 com.android.settings.development.OverlayCategoryPreferenceController。
 * <p>
 * 对照 smali 的实现：
 * <ul>
 *   <li>构造：PackageManager 来自 Context，IOverlayManager 来自
 *       ServiceManager.getService("overlay") + IOverlayManager.Stub.asInterface，
 *       mAvailable = (mOverlayManager != null && !getOverlayInfos().isEmpty())。</li>
 *   <li>getPreferenceKey() 返回 mCategory（即 XML 里 ListPreference 的 key）。</li>
 *   <li>displayPreference(screen)：findPreference(mCategory) 拿到 ListPreference 并保存。</li>
 *   <li>getOverlayInfos()：getOverlayInfosForTarget("android", 0)，按 OverlayInfo.category 过滤，
 *       按 OverlayInfo.priority 升序排序。</li>
 *   <li>updateState()：第一项固定 package_device_default，再追加每个 overlay 的包名(entryValue)
 *       与应用名(entry)；把当前 isEnabled() 的那一项设为 value/summary。</li>
 *   <li>onPreferenceChange() → setOverlay()：先取当前已启用包；若选 default 且 current 为空、
 *       或 newPkg 与 current 相同，直接返回 true；否则起 AsyncTask 真正下发切换，
 *       onPostExecute 里再 updateState。</li>
 * </ul>
 * 由于 IOverlayManager / OverlayInfo 均为隐藏 API，这里全部用反射，纯 Gradle 工程无需 framework.jar。
 * 平台签名 + system uid 的 App 享有 hidden-api 豁免，反射可正常调用。
 */
public class OverlayCategoryPreferenceController implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "OverlayCategoryCtrl";

    /** 对应 smali 里的 PACKAGE_DEVICE_DEFAULT 常量 */
    static final String PACKAGE_DEVICE_DEFAULT = "package_device_default";

    private final Context mContext;
    private final String mCategory;
    private final Object mOverlayManager;   // android.content.om.IOverlayManager
    private final PackageManager mPackageManager;
    private final boolean mAvailable;

    /** 对应 smali 的 mPreference:androidx.preference.ListPreference */
    private ListPreference mPreference;

    public OverlayCategoryPreferenceController(Context context, String category) {
        mContext = context;
        mPackageManager = context.getPackageManager();
        mCategory = category;
        mOverlayManager = getIOverlayManager();
        mAvailable = mOverlayManager != null && !getOverlayInfos().isEmpty();
    }

    // ------------------------------------------------------------------
    // smali: getPreferenceKey() / isAvailable()
    // ------------------------------------------------------------------

    /** 对应 getPreferenceKey()：返回 mCategory */
    public String getPreferenceKey() {
        return mCategory;
    }

    /** 对应 isAvailable() */
    public boolean isAvailable() {
        return mAvailable;
    }

    // ------------------------------------------------------------------
    // smali: displayPreference(PreferenceScreen) / setPreference(ListPreference)
    // ------------------------------------------------------------------

    /** 对应 displayPreference()：在 PreferenceScreen 里按 key 找到 ListPreference 并保存 */
    public void displayPreference(PreferenceScreen screen) {
        Preference p = screen.findPreference(getPreferenceKey());
        if (p instanceof ListPreference) {
            mPreference = (ListPreference) p;
            mPreference.setOnPreferenceChangeListener(this);
        }
    }

    // ------------------------------------------------------------------
    // smali: getOverlayInfos()
    // ------------------------------------------------------------------

    /**
     * 对应 getOverlayInfos()：getOverlayInfosForTarget("android", 0) 后按 category 过滤，
     * 再按 OverlayInfo.priority 升序排序。
     */
    private List<Object> getOverlayInfos() {
        List<Object> result = new ArrayList<>();
        if (mOverlayManager == null) {
            return result;
        }
        try {
            Method m = mOverlayManager.getClass()
                    .getMethod("getOverlayInfosForTarget", String.class, int.class);
            List<?> infos = (List<?>) m.invoke(mOverlayManager, "android", 0);
            for (Object info : infos) {
                String cat = getOverlayInfoField(info, "category", String.class);
                if (mCategory.equals(cat)) {
                    result.add(info);
                }
            }
            // OVERLAY_INFO_COMPARATOR = Comparator.comparingInt(info -> info.priority)
            final Field priority = result.isEmpty() ? null
                    : findField(result.get(0).getClass(), "priority");
            if (priority != null) {
                Collections.sort(result, new Comparator<Object>() {
                    @Override
                    public int compare(Object o1, Object o2) {
                        try {
                            int p1 = priority.getInt(o1);
                            int p2 = priority.getInt(o2);
                            return Integer.compare(p1, p2);
                        } catch (Throwable t) {
                            return 0;
                        }
                    }
                });
            }
        } catch (InvocationTargetException ite) {
            // 反射调用底层 AIDL：RemoteException 被包装，还原 smali 的 rethrowFromSystemServer()
            Throwable cause = ite.getCause();
            if (cause instanceof RemoteException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    throw ((RemoteException) cause).rethrowFromSystemServer();
                }
            }
            throw new RuntimeException(cause);
        } catch (Throwable t) {
            Log.w(TAG, "getOverlayInfos failed", t);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // smali: updateState(Preference)
    // 直接操作 ListPreference，与原实现一致
    // ------------------------------------------------------------------

    /**
     * 对应 updateState(Preference)：把 package_device_default 作为首项，
     * 再追加每个 overlay 的包名(entryValue)/应用名(entry)；把当前 isEnabled() 的那一项
     * 设为 ListPreference 的 value 与 summary。
     */
    public void updateState() {
        if (mPreference == null) {
            return;
        }
        List<String> entryValues = new ArrayList<>();
        List<String> entries = new ArrayList<>();

        // 首项：package_device_default + "设备默认"（原资源 0x7f120a7d）
        entryValues.add(PACKAGE_DEVICE_DEFAULT);
        entries.add(mContext.getString(R.string.theme_customization_device_default));

        String selValue = entryValues.get(0);
        String selSummary = entries.get(0);

        for (Object info : getOverlayInfos()) {
            String pkg = getOverlayInfoField(info, "packageName", String.class);
            entryValues.add(pkg);

            String label;
            try {
                ApplicationInfo ai = mPackageManager.getApplicationInfo(pkg, 0);
                label = ai.loadLabel(mPackageManager).toString();
            } catch (PackageManager.NameNotFoundException e) {
                // smali: catch NameNotFoundException 时用 packageName 作为 label
                label = pkg;
            }
            entries.add(label);

            if (isEnabled(info)) {
                selValue = pkg;
                selSummary = label;
            }
        }

        // smali: setEntries / setEntryValues / setValue / setSummary
        mPreference.setEntries(entries.toArray(new CharSequence[0]));
        mPreference.setEntryValues(entryValues.toArray(new CharSequence[0]));
        mPreference.setValue(selValue);
        mPreference.setSummary(selSummary);
    }

    // ------------------------------------------------------------------
    // smali: onPreferenceChange() -> setOverlay(String)
    // ------------------------------------------------------------------

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        // smali: onPreferenceChange(p, value) -> setOverlay((String) value)
        return setOverlay((String) newValue);
    }

    /**
     * 对应 setOverlay(String)：
     * 1) 取当前已启用包 currentPkg（stream.filter(isEnabled).map(packageName).findFirst().orElse(null)）
     * 2) 若 PACKAGE_DEVICE_DEFAULT.equals(newPkg) 且 currentPkg 为空 → 直接返回 true
     *    若 newPkg 与 currentPkg 相同 → 直接返回 true
     * 3) 否则起 AsyncTask 真正下发：选 default 则禁用 currentPkg，否则 setEnabledExclusiveInCategory(newPkg)
     */
    private boolean setOverlay(final String newPkg) {
        String currentPkg = null;
        for (Object info : getOverlayInfos()) {
            if (isEnabled(info)) {
                currentPkg = getOverlayInfoField(info, "packageName", String.class);
                break;
            }
        }

        // smali: if (PACKAGE_DEVICE_DEFAULT.equals(newPkg) && TextUtils.isEmpty(currentPkg)) return true;
        if (PACKAGE_DEVICE_DEFAULT.equals(newPkg) && TextUtils.isEmpty(currentPkg)) {
            return true;
        }
        // smali: if (TextUtils.equals(newPkg, currentPkg)) return true;
        if (TextUtils.equals(newPkg, currentPkg)) {
            return true;
        }

        // smali: new OverlayCategoryPreferenceController$1(this, newPkg, currentPkg).execute()
        new SetOverlayTask(newPkg, currentPkg).execute();
        return true;
    }

    /** 对应 onDeveloperOptionsSwitchDisabled()：回到 package_device_default 再刷新 */
    public void onDisabled() {
        setOverlay(PACKAGE_DEVICE_DEFAULT);
        updateState();
    }

    // ------------------------------------------------------------------
    // OverlayCategoryPreferenceController$1 的等价实现
    // ------------------------------------------------------------------

    private class SetOverlayTask extends AsyncTask<Void, Void, Void> {
        private final String mNewPackage;
        private final String mCurrentPackage;

        SetOverlayTask(String newPackage, String currentPackage) {
            mNewPackage = newPackage;
            mCurrentPackage = currentPackage;
        }

        @Override
        protected Void doInBackground(Void... params) {
            // UserHandle.myUserId() 是 @hide 静态方法，反射调用（与 smali 取当前用户一致）
            final int userId = myUserId();
            try {
                if (mOverlayManager == null) {
                    return null;
                }
                if (PACKAGE_DEVICE_DEFAULT.equals(mNewPackage)) {
                    // 选“设备默认”：禁用当前已启用包
                    if (!TextUtils.isEmpty(mCurrentPackage)) {
                        Method m = mOverlayManager.getClass()
                                .getMethod("setEnabled", String.class, boolean.class, int.class);
                        m.invoke(mOverlayManager, mCurrentPackage, false, userId);
                    }
                } else {
                    // smali: mOverlayManager.setEnabledExclusiveInCategory(mNewPackage, userId)
                    Method m = mOverlayManager.getClass()
                            .getMethod("setEnabledExclusiveInCategory", String.class, int.class);
                    m.invoke(mOverlayManager, mNewPackage, userId);
                }
            } catch (InvocationTargetException ite) {
                // 反射调用底层 AIDL：解包 RemoteException 后记录
                Throwable cause = ite.getCause();
                if (cause instanceof RemoteException) {
                    Log.w(TAG, "setEnabled failed (RemoteException)", (RemoteException) cause);
                } else {
                    Log.w(TAG, "setEnabled failed", cause);
                }
            } catch (Throwable t) {
                Log.w(TAG, "setEnabled failed", t);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            // 对应原实现 onPostExecute 里的 updateState(mPreference)
            updateState();
        }
    }

    // ------------------------------------------------------------------
    // 反射工具
    // ------------------------------------------------------------------

    /** 通过 ServiceManager.getService("overlay") + IOverlayManager.Stub.asInterface 获取 IOverlayManager */
    private static Object getIOverlayManager() {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            IBinder binder = (IBinder) sm.getMethod("getService", String.class)
                    .invoke(null, "overlay");
            if (binder == null) {
                return null;
            }
            Class<?> stub = Class.forName("android.content.om.IOverlayManager$Stub");
            return stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
        } catch (Throwable t) {
            Log.w(TAG, "getIOverlayManager failed", t);
            return null;
        }
    }

    private static boolean isEnabled(Object info) {
        try {
            Method m = info.getClass().getMethod("isEnabled");
            return (boolean) m.invoke(info);
        } catch (Throwable t) {
            return false;
        }
    }

    private static <T> T getOverlayInfoField(Object info, String name, Class<T> type) {
        try {
            Field f = findField(info.getClass(), name);
            if (f != null) {
                f.setAccessible(true);
                return type.cast(f.get(info));
            }
        } catch (Throwable t) {
            // ignore
        }
        return null;
    }

    private static Field findField(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                // continue up
            }
        }
        return null;
    }

    /** 反射调用 android.os.UserHandle.myUserId() 获取当前用户 id */
    private static int myUserId() {
        try {
            Method m = Class.forName("android.os.UserHandle").getMethod("myUserId");
            return (int) m.invoke(null);
        } catch (Throwable t) {
            return 0;
        }
    }
}