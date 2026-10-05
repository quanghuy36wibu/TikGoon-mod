package dev.tiktokrootmod;

import android.app.Application;
import android.content.Context;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class Entry implements IXposedHookLoadPackage {
    private static final String TARGET = "com.ss.android.ugc.trill";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if ("com.miui.home".equals(param.packageName) && "com.miui.home".equals(param.processName)) {
            IconHooks.install(param.classLoader);
            return;
        }
        if (!TARGET.equals(param.packageName) || !TARGET.equals(param.processName)) return;
        try {
            XposedBridge.hookAllMethods(Application.class, "attach", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam hook) {
                    try {
                        Config.load((Context) hook.args[0]);
                        FeedHooks.install(param.classLoader);
                        if (Config.CLEAN_SHARE_LINKS) CleanLinkHooks.install();
                        if (Config.SPOOF_REGION) RegionHooks.install();
                        if (Config.ALLOW_SCREENSHOTS) ScreenCaptureHooks.install();
                        if (Config.ENABLE_PROFILE_BACKGROUND) ProfileBackgroundHooks.install(param.classLoader);
                        VideoHooks.install(param.classLoader);
                        MediaHooks.install(param.classLoader);
                        MessageHooks.install(param.classLoader);
                        AppearanceHooks.install();
                        ThemeHooks.install();
                        LiveTranslationHooks.install(param.classLoader);
                        XposedBridge.log("TikTokRootMod: hooks installed for " + TARGET);
                    } catch (Throwable error) {
                        XposedBridge.log("TikTokRootMod: hook setup failed");
                        XposedBridge.log(error);
                    }
                }
            });
        } catch (Throwable error) {
            XposedBridge.log("TikTokRootMod: hook setup failed");
            XposedBridge.log(error);
        }
    }
}
