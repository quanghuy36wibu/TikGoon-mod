package dev.tiktokrootmod;

import android.media.PlaybackParams;

import java.lang.reflect.Method;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Dừng lặp video và đặt tốc độ phát mặc định.
 * Dùng lớp TTVideoEngine của ByteDance (tên không bị làm rối), tìm các hàm "loop" kiểu boolean và ép về false.
 */
final class PlaybackHooks {
    private static int logs;
    private static int speedLogs;

    private PlaybackHooks() {}

    static void install(ClassLoader loader) {
        Class<?> engine = XposedHelpers.findClassIfExists("com.ss.ttvideoengine.TTVideoEngine", loader);
        if (engine == null) {
            HookLog.log("TikTokRootMod: playback: TTVideoEngine not found");
            return;
        }
        if (Config.STOP_LOOP) hookLoop(engine);
        if (Config.DEFAULT_SPEED > 0) hookSpeed(engine);
    }

    private static void hookLoop(Class<?> engine) {
        int count = 0;
        for (Method method : engine.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (types.length != 1 || types[0] != boolean.class) continue;
            if (!method.getName().toLowerCase(Locale.ROOT).contains("loop")) continue;
            final String name = method.getName();
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (Boolean.TRUE.equals(param.args[0])) {
                        param.args[0] = Boolean.FALSE;
                        if (logs++ < 5) HookLog.log("TikTokRootMod: stop-loop forced " + name + "(false)");
                    }
                }
            });
            count++;
        }
        HookLog.log("TikTokRootMod: stop-loop hooked " + count + " method(s) on TTVideoEngine");
    }

    /** Mỗi lần video bắt đầu phát (play) thì đặt lại tốc độ mặc định. */
    private static void hookSpeed(Class<?> engine) {
        int count = 0;
        for (Method method : engine.getDeclaredMethods()) {
            if (!method.getName().equals("play") || method.getParameterTypes().length != 0) continue;
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    applySpeed(param.thisObject);
                }
            });
            count++;
        }
        HookLog.log("TikTokRootMod: default speed " + Config.DEFAULT_SPEED + "% hooked " + count + " play() method(s)");
    }

    private static void applySpeed(Object engine) {
        final float speed = Config.DEFAULT_SPEED / 100f;
        try {
            for (Method method : engine.getClass().getMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!method.getName().equals("setPlaybackSpeed") || types.length != 1) continue;
                if (types[0] == float.class) { method.invoke(engine, Float.valueOf(speed)); return; }
                if (types[0] == double.class) { method.invoke(engine, Double.valueOf(speed)); return; }
            }
            for (Method method : engine.getClass().getMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!method.getName().equals("setPlaybackParams") || types.length != 1
                        || types[0] != PlaybackParams.class) continue;
                PlaybackParams params = new PlaybackParams();
                params.setSpeed(speed);
                method.invoke(engine, params);
                return;
            }
            if (speedLogs++ < 2) HookLog.log("TikTokRootMod: default speed: no speed setter on TTVideoEngine");
        } catch (Throwable error) {
            if (speedLogs++ < 3) HookLog.log("TikTokRootMod: default speed failed: " + error);
        }
    }
}
