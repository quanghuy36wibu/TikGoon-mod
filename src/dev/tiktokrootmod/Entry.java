package dev.tiktokrootmod;

import android.app.Application;
import android.content.Context;

import dev.tiktokrootmod.dexkit.DexKitRuntime;

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
    private static volatile DexKitRuntime dexKitRuntime;

    /** Shared DexKit runtime for hook groups that need dynamic class/method lookup. */
    public static DexKitRuntime getDexKitRuntime() {
        return dexKitRuntime;
    }

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
                        Context context = (Context) hook.args[0];
                        HookLog.init(context);
                        initializeDexKit(context);
                        boolean ok = true;
                        ok &= step("feed", () -> FeedHooks.install(param.classLoader));
                        if (Config.CLEAN_SHARE_LINKS) ok &= step("clean-links", CleanLinkHooks::install);
                        if (Config.SPOOF_REGION) ok &= step("region", RegionHooks::install);
                        if (Config.ALLOW_SCREENSHOTS) ok &= step("screenshots", ScreenCaptureHooks::install);
                        if (Config.ENABLE_PROFILE_BACKGROUND)
                            ok &= step("profile-bg", () -> ProfileBackgroundHooks.install(param.classLoader));
                        ok &= step("video", () -> VideoHooks.install(param.classLoader));
                        ok &= step("media", () -> MediaHooks.install(param.classLoader));
                        ok &= step("message", () -> MessageHooks.install(param.classLoader));
                        ok &= step("appearance", AppearanceHooks::install);
                        ok &= step("theme", ThemeHooks::install);
                        ok &= step("live-translation", () -> LiveTranslationHooks.install(param.classLoader));
                        if (Config.DISABLE_DOUBLE_TAP_LIKE) ok &= step("double-tap", DoubleTapHooks::install);
                        HookLog.log("TikTokRootMod: hooks installed for " + TARGET);
                        writeStatus((Context) hook.args[0], ok);
                    } catch (Throwable error) {
                        HookLog.log("TikTokRootMod: hook setup failed");
                        HookLog.log(error);
                        writeStatus((Context) hook.args[0], false);
                    }
                }
            });
        } catch (Throwable error) {
            HookLog.log("TikTokRootMod: hook setup failed");
            HookLog.log(error);
        }
    }

    /** Initialize one process-scoped bridge against the installed TikTok APK. */
    private static synchronized void initializeDexKit(Context context) {
        if (dexKitRuntime != null) return;
        try {
            String apkPath = context.getApplicationInfo().sourceDir;
            dexKitRuntime = DexKitRuntime.open(apkPath);
            HookLog.log("TikGoon: DexKit ready; dex count=" + dexKitRuntime.getDexCount());
        } catch (Throwable error) {
            // DexKit is optional during rollout: keep the existing static hooks alive.
            HookLog.log("TikGoon: DexKit initialization failed; continuing with existing hooks");
            HookLog.log(error);
        }
    }

    private interface Step { void run() throws Throwable; }

    /** Chạy một nhóm hook; nếu lỗi thì ghi log và vẫn cho các nhóm khác chạy tiếp. */
    private static boolean step(String name, Step step) {
        try {
            step.run();
            HookLog.log("TikTokRootMod: hook group '" + name + "' ok");
            return true;
        } catch (Throwable error) {
            HookLog.log("TikTokRootMod: hook group '" + name + "' failed");
            HookLog.log(error);
            return false;
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
            HookLog.log("TikTokRootMod: self hook failed: " + error);
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
