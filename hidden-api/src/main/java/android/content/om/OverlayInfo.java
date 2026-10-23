package android.content.om;

public class OverlayInfo {
    public String packageName;
    public String category;
    public int priority;

    public boolean isEnabled() {
        throw new RuntimeException("Stub!");
    }
}
