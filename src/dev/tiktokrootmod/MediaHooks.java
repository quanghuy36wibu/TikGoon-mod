package dev.tiktokrootmod;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class MediaHooks {
    private MediaHooks() {}

    static void install(ClassLoader loader) {
        if (!Config.REMOVE_DOWNLOAD_WATERMARK) return;
        Class<?> video = XposedHelpers.findClassIfExists("com.ss.android.ugc.aweme.feed.model.Video", loader);
        if (video != null) {
            XC_MethodHook source = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object clean = XposedHelpers.callMethod(param.thisObject, "getDownloadNoWatermarkAddr");
                        if (clean != null) param.setResult(clean);
                    } catch (Throwable error) { XposedBridge.log("TikTokRootMod: no-watermark source unavailable: " + error); }
                }
            };
            XposedBridge.hookAllMethods(video, "getDownloadAddr", source);
            XposedBridge.hookAllMethods(video, "getNewDownloadAddr", source);
            XposedBridge.log("TikTokRootMod: hooked video download URL selection");
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
                            XposedBridge.log("TikTokRootMod: selected clean download source");
                        } catch (Throwable error) {
                            XposedBridge.log("TikTokRootMod: clean source selection failed: " + error);
                        }
                    }
                });
                XposedBridge.log("TikTokRootMod: hooked download source selector");
            } catch (Throwable error) { XposedBridge.log("TikTokRootMod: download selector hook: " + error); }
        }
        Class<?> aweme = XposedHelpers.findClassIfExists("com.ss.android.ugc.aweme.feed.model.Aweme", loader);
        if (aweme != null) {
            XposedBridge.hookAllMethods(aweme, "getDownloadWithoutWatermark", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) { param.setResult(true); }
            });
            XposedBridge.hookAllMethods(aweme, "needTTSWatermarkWhenDownload", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) { param.setResult(false); }
            });
        }
        Class<?> type = XposedHelpers.findClassIfExists(
                "com.ss.android.ugc.trill.download.configuration.AwemeVideoDownloadConfiguration", loader);
        if (type == null) return;
        for (Method method : type.getDeclaredMethods()) {
            if (!"LJFF".equals(method.getName()) || method.getParameterTypes().length != 1 ||
                    !method.getReturnType().getName().endsWith("WaterMarkAbilityProtocol")) continue;
            try {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) { param.setResult(null); }
                });
                XposedBridge.log("TikTokRootMod: native video watermark ability disabled");
            } catch (Throwable error) { XposedBridge.log("TikTokRootMod: watermark hook: " + error); }
        }
    }
}
