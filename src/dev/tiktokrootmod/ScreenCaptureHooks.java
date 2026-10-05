package dev.tiktokrootmod;

import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

final class ScreenCaptureHooks {
    private ScreenCaptureHooks() {}

    static void install() {
        final int secure = WindowManager.LayoutParams.FLAG_SECURE;
        XposedBridge.hookAllMethods(Window.class, "addFlags", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length > 0 && param.args[0] instanceof Integer)
                    param.args[0] = (Integer) param.args[0] & ~secure;
            }
        });
        XposedBridge.hookAllMethods(Window.class, "setFlags", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length > 1 && param.args[0] instanceof Integer && param.args[1] instanceof Integer) {
                    param.args[0] = (Integer) param.args[0] & ~secure;
                    param.args[1] = (Integer) param.args[1] & ~secure;
                }
            }
        });
        XposedBridge.hookAllMethods(SurfaceView.class, "setSecure", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length > 0) param.args[0] = false;
            }
        });
    }
}
