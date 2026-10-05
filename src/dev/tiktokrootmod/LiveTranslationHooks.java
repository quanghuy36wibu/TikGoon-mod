package dev.tiktokrootmod;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Disable automatic public-screen comment translation inside TikTok LIVE only. */
final class LiveTranslationHooks {
    private LiveTranslationHooks() {}

    static void install(ClassLoader loader) {
        if (!Config.DISABLE_LIVE_AUTO_TRANSLATE) return;
        try {
            Class<?> setting = XposedHelpers.findClassIfExists(
                    "com.bytedance.android.livesdk.livesetting.publicscreen.LiveAutoTranslateStateSetting", loader);
            if (setting != null) {
                XposedBridge.hookAllMethods(setting, "getValue", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        param.setResult(false);
                    }
                });
                XposedBridge.log("TikTokRootMod: LIVE auto-translation default disabled");
            }
        } catch (Throwable error) {
            XposedBridge.log("TikTokRootMod: LIVE translation setting hook: " + error);
        }
        try {
            // TikTok 47.0.3 public-screen controller. This method gates auto translation
            // after room, audience and saved-state checks; manual translation is separate.
            Class<?> publicScreen = XposedHelpers.findClassIfExists("X.0LbU", loader);
            if (publicScreen != null) {
                XposedHelpers.findAndHookMethod(publicScreen, "LJLJI", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        param.setResult(false);
                    }
                });
                XposedBridge.log("TikTokRootMod: LIVE auto-translation gate disabled");
            }
        } catch (Throwable error) {
            XposedBridge.log("TikTokRootMod: LIVE translation gate hook: " + error);
        }
    }
}
