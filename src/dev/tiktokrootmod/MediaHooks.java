package dev.tiktokrootmod;

import java.lang.reflect.Method;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class MediaHooks {
    private MediaHooks() {}

    private static int fired;

    private static final java.util.HashMap<String, Integer> TRACES = new java.util.HashMap<>();

    private static void fire(String what) {
        if (fired++ < 30) HookLog.log("TikTokRootMod: watermark hook fired: " + what);
        if (!Config.LOG_ENABLED) return;
        Integer n = TRACES.get(what);
        if (n != null && n >= 1) return;       // mỗi loại chỉ ghi ngăn xếp gọi 1 lần
        TRACES.put(what, 1);
        StringBuilder sb = new StringBuilder("caller of " + what + ": ");
        int count = 0;
        for (StackTraceElement frame : new Throwable().getStackTrace()) {
            String cls = frame.getClassName();
            if (cls.startsWith("de.robv") || cls.startsWith("LSPHooker") || cls.startsWith("dev.tiktokrootmod")
                    || cls.startsWith("java.lang.reflect") || cls.startsWith("org.lsposed")) continue;
            sb.append(cls).append('.').append(frame.getMethodName()).append(" < ");
            if (++count >= 8) break;
        }
        HookLog.log("TikTokRootMod: " + sb);
    }

    static void install(ClassLoader loader) {
        if (!Config.REMOVE_DOWNLOAD_WATERMARK) {
            HookLog.log("TikTokRootMod: watermark removal is OFF in settings (remove_download_watermark=false)");
            return;
        }
        HookLog.log("TikTokRootMod: watermark removal is ON, installing media hooks");
        Class<?> video = XposedHelpers.findClassIfExists("com.ss.android.ugc.aweme.feed.model.Video", loader);
        if (video != null) {
            XC_MethodHook source = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        String via = "noWatermark";
                        Object clean = XposedHelpers.callMethod(param.thisObject, "getDownloadNoWatermarkAddr");
                        if (clean == null) {
                            // Không có địa chỉ tải sạch: dùng địa chỉ phát (thường không có watermark)
                            via = "playH264";
                            clean = XposedHelpers.callMethod(param.thisObject, "getPlayAddrH264");
                        }
                        if (clean == null) {
                            via = "play";
                            clean = XposedHelpers.callMethod(param.thisObject, "getPlayAddr");
                        }
                        if (clean != null) { param.setResult(clean); fire("Video." + param.method.getName() + " via " + via); }
                        else fire("Video." + param.method.getName() + " NO clean source at all");
                    } catch (Throwable error) { HookLog.log("TikTokRootMod: no-watermark source unavailable: " + error); }
                }
            };
            Set<?> a = XposedBridge.hookAllMethods(video, "getDownloadAddr", source);
            Set<?> b = XposedBridge.hookAllMethods(video, "getNewDownloadAddr", source);
            HookLog.log("TikTokRootMod: Video class found; getDownloadAddr hooks=" + a.size()
                    + ", getNewDownloadAddr hooks=" + b.size());
        } else {
            HookLog.log("TikTokRootMod: MISSING class com.ss.android.ugc.aweme.feed.model.Video");
        }
        Class<?> selector = XposedHelpers.findClassIfExists("X.0ztG", loader);
        if (selector != null) {
            try {
                installSelector(selector, loader);
            } catch (Throwable error) { HookLog.log("TikTokRootMod: download selector hook failed: " + error); }
        } else {
            HookLog.log("TikTokRootMod: MISSING class X.0ztG (tên bị làm rối, đổi theo từng bản TikTok)");
        }
        Class<?> aweme = XposedHelpers.findClassIfExists("com.ss.android.ugc.aweme.feed.model.Aweme", loader);
        if (aweme != null) {
            Set<?> c = XposedBridge.hookAllMethods(aweme, "getDownloadWithoutWatermark", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    param.setResult(true);
                    fire("Aweme.getDownloadWithoutWatermark");
                }
            });
            Set<?> d = XposedBridge.hookAllMethods(aweme, "needTTSWatermarkWhenDownload", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    param.setResult(false);
                    fire("Aweme.needTTSWatermarkWhenDownload");
                }
            });
            HookLog.log("TikTokRootMod: Aweme hooks: getDownloadWithoutWatermark=" + c.size()
                    + ", needTTSWatermarkWhenDownload=" + d.size());
        }
        Class<?> type = XposedHelpers.findClassIfExists(
                "com.ss.android.ugc.trill.download.configuration.AwemeVideoDownloadConfiguration", loader);
        if (type == null) {
            HookLog.log("TikTokRootMod: MISSING class AwemeVideoDownloadConfiguration");
            return;
        }
        int hooked = 0;
        // Không ràng buộc tên hàm (LJFF bị làm rối, đổi theo bản): chỉ cần 1 tham số và trả về *WaterMarkAbilityProtocol.
        for (Method method : type.getDeclaredMethods()) {
            if (method.getParameterTypes().length != 1 ||
                    !method.getReturnType().getName().endsWith("WaterMarkAbilityProtocol")) continue;
            try {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(null);
                        fire("native watermark ability " + param.method.getName());
                    }
                });
                hooked++;
                HookLog.log("TikTokRootMod: native video watermark ability disabled via " + method.getName());
            } catch (Throwable error) { HookLog.log("TikTokRootMod: watermark hook: " + error); }
        }
        if (hooked == 0) {
            StringBuilder names = new StringBuilder();
            for (Method method : type.getDeclaredMethods())
                names.append(method.getName()).append("(").append(method.getParameterTypes().length)
                        .append(")->").append(method.getReturnType().getSimpleName()).append(" ");
            HookLog.log("TikTokRootMod: NO WaterMarkAbilityProtocol method found; methods: " + names);
        }
    }

    /**
     * Bộ chọn nguồn tải: tên hàm LIZIZ và trường LIZ/LIZJ bị làm rối nên đổi theo bản TikTok.
     * Tìm theo kiểu thay vì theo tên: hàm có đúng 1 tham số là Aweme; trường cùng kiểu với địa chỉ sạch.
     */
    private static void installSelector(Class<?> selector, ClassLoader loader) throws Throwable {
        final Class<?> awemeType = XposedHelpers.findClass("com.ss.android.ugc.aweme.feed.model.Aweme", loader);
        StringBuilder dump = new StringBuilder("X.0ztG members: ");
        for (Method m : selector.getDeclaredMethods()) {
            dump.append(m.getName()).append("(");
            for (Class<?> t : m.getParameterTypes()) dump.append(t.getSimpleName()).append(",");
            dump.append(")->").append(m.getReturnType().getSimpleName()).append(" ");
        }
        for (java.lang.reflect.Field f : selector.getDeclaredFields())
            dump.append("[").append(f.getName()).append(":").append(f.getType().getSimpleName()).append("] ");
        HookLog.log("TikTokRootMod: " + dump);

        int hooked = 0;
        for (Method method : selector.getDeclaredMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1 || params[0] != awemeType) continue;
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object videoObject = XposedHelpers.callMethod(param.args[0], "getVideo");
                        if (videoObject == null) return;
                        Object clean = XposedHelpers.callMethod(videoObject, "getDownloadNoWatermarkAddr");
                        if (clean == null) clean = XposedHelpers.callMethod(videoObject, "getPlayAddrH264");
                        if (clean == null) return;
                        int set = 0;
                        for (java.lang.reflect.Field f : param.thisObject.getClass().getDeclaredFields()) {
                            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                            f.setAccessible(true);
                            if (f.getType().isInstance(clean) && f.getType() != Object.class) {
                                f.set(param.thisObject, clean);
                                set++;
                            } else if (f.getType() == boolean.class && "LIZJ".equals(f.getName())) {
                                f.setBoolean(param.thisObject, false);
                            }
                        }
                        fire("selector " + param.method.getName() + " fields set=" + set
                                + " type=" + clean.getClass().getSimpleName());
                    } catch (Throwable error) {
                        HookLog.log("TikTokRootMod: clean source selection failed: " + error);
                    }
                }
            });
            hooked++;
            HookLog.log("TikTokRootMod: hooked download selector " + method.getName());
        }
        if (hooked == 0) HookLog.log("TikTokRootMod: no Aweme-taking method in X.0ztG");
    }
}
