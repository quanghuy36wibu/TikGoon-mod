package dev.tiktokrootmod;

import java.lang.reflect.Method;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class MediaHooks {
    private MediaHooks() {}

    private static int fired;

    private static void fire(String what) {
        if (fired++ < 30) HookLog.log("TikTokRootMod: watermark hook fired: " + what);
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
                        Object clean = XposedHelpers.callMethod(param.thisObject, "getDownloadNoWatermarkAddr");
                        if (clean != null) { param.setResult(clean); fire("Video." + param.method.getName()); }
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
                Class<?> awemeType = XposedHelpers.findClass(
                        "com.ss.android.ugc.aweme.feed.model.Aweme", loader);
                XposedHelpers.findAndHookMethod(selector, "LIZIZ", awemeType, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Object awemeObject = param.args[0];
                            Object videoObject = XposedHelpers.callMethod(awemeObject, "getVideo");
                            if (videoObject == null) return;
                            Object clean = XposedHelpers.callMethod(videoObject, "getDownloadNoWatermarkAddr");
                            if (clean == null) clean = XposedHelpers.callMethod(videoObject, "getPlayAddrH264");
                            if (clean == null) return;
                            XposedHelpers.setObjectField(param.thisObject, "LIZ", clean);
                            XposedHelpers.setBooleanField(param.thisObject, "LIZJ", false);
                            fire("selector X.0ztG.LIZIZ");
                        } catch (Throwable error) {
                            HookLog.log("TikTokRootMod: clean source selection failed: " + error);
                        }
                    }
                });
                HookLog.log("TikTokRootMod: hooked download source selector X.0ztG.LIZIZ");
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
}
