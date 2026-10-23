package android.content.om;

import android.os.IBinder;
import android.os.RemoteException;

import java.util.List;

public interface IOverlayManager {
    List<OverlayInfo> getOverlayInfosForTarget(String targetPackageName, int userId) throws RemoteException;

    boolean setEnabled(String packageName, boolean enable, int userId) throws RemoteException;

    boolean setEnabledExclusiveInCategory(String packageName, int userId) throws RemoteException;

    class Stub {
        public static IOverlayManager asInterface(IBinder binder) {
            throw new RuntimeException("Stub!");
        }
    }
}
