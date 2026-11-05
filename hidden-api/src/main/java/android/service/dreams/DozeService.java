package android.service.dreams;

/**
 * {@code android.service.dreams.DozeService} 的编译期桩。
 *
 * 该类属于 @SystemApi / hidden，未包含在公开 SDK 的 android.jar 里，
 * 但熄灭屏幕（doze）时系统会绑定配置项 {@code config_dozeComponent} 指向的服务，
 * 它必须是这个类的子类才能控制 DOZE 面板状态。
 *
 * 本模块是 compileOnly，编译产物不会打进 APK；运行时由 boot classpath 上的真实
 * framework 类接管，因此这里只需要保证方法名与描述符一致。
 */
public abstract class DozeService extends DreamService {

    public DozeService() {
    }

    /** 请求切换显示状态（面板 DOZE / DOZE_SUSPEND / OFF 等） */
    public void requestDisplayState(int state, int brightness) {
        throw new RuntimeException("Stub!");
    }

    /** 设置 doze 时的面板状态，例如 {@code Display.STATE_DOZE} */
    public void setDozeScreenState(int state) {
        throw new RuntimeException("Stub!");
    }

    /** 设置 doze 时的背光亮度，-1 表示不覆盖 */
    public void setDozeScreenBrightness(int brightness) {
        throw new RuntimeException("Stub!");
    }

    /** 结束本次 doze */
    public void finishDoze() {
        throw new RuntimeException("Stub!");
    }
}
