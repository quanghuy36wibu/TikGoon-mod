package dev.tiktokrootmod;

import android.app.Application;
import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class Entry implements IXposedHookLoadPackage {
    private static final String TARGET = "com.ss.android.ugc.trill";
    private static final String OWN = "dev.tiktokrootmod";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (OWN.equals(param.packageName)) {
            installSelfHook(param.classLoader);
            return;
        }
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
                        writeStatus((Context) hook.args[0], true);
                    } catch (Throwable error) {
                        XposedBridge.log("TikTokRootMod: hook setup failed");
                        XposedBridge.log(error);
                        writeStatus((Context) hook.args[0], false);
                    }
                }
            });
        } catch (Throwable error) {
            XposedBridge.log("TikTokRootMod: hook setup failed");
            XposedBridge.log(error);
        }
    }

    /** Cho phép màn hình cài đặt biết module đang được Xposed nạp (số API của framework). */
    private static void installSelfHook(ClassLoader loader) {
        try {
            XposedHelpers.findAndHookMethod(OWN + ".SettingsActivity", loader, "xposedApiVersion",
                    new XC_MethodReplacement() {
                        @Override protected Object replaceHookedMethod(MethodHookParam hook) {
                            return XposedBridge.getXposedVersion();
                        }
                    });
        } catch (Throwable error) {
            XposedBridge.log("TikTokRootMod: self hook failed: " + error);
        }
    }

    /** Ghi tệp trạng thái trong thư mục files của TikTok để màn hình cài đặt (có root) đọc lại. */
    private static void writeStatus(Context context, boolean ok) {
        try {
            String text = "pid=" + android.os.Process.myPid() + "\nloaded=" + System.currentTimeMillis()
                    + "\nok=" + (ok ? 1 : 0) + "\n";
            try (FileOutputStream output = new FileOutputStream(
                    new File(context.getFilesDir(), "tiktokrootmod_status"))) {
                output.write(text.getBytes("UTF-8"));
            }
        } catch (Throwable ignored) { }
    }
}
