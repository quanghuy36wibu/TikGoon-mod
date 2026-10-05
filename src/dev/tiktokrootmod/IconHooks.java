package dev.tiktokrootmod;

import android.content.pm.LauncherActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.File;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Optional icon replacement for the MIUI Home launcher on this device. */
final class IconHooks {
    static final String ICON_PATH = "/data/user/0/com.miui.home/files/tiktokrootmod_icon.png";
    private static final String TARGET = "com.ss.android.ugc.trill";
    private static Bitmap icon;
    private IconHooks() {}

    static void install(ClassLoader loader) {
        if (!new File(ICON_PATH).isFile()) return;
        try {
            Class<?> provider = XposedHelpers.findClassIfExists("com.miui.home.icon.IconProvider", loader);
            if (provider == null) return;
            XC_MethodHook replacement = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!matches(param.args)) return;
                    Drawable drawable = iconDrawable();
                    if (drawable != null) param.setResult(drawable);
                }
            };
            XposedBridge.hookAllMethods(provider, "getActivityIcon", replacement);
            XposedBridge.hookAllMethods(provider, "getRawIcon", replacement);
            XposedBridge.hookAllMethods(provider, "getCustomizedIcon", replacement);
            XposedBridge.log("TikTokRootMod: MIUI Home TikTok icon hook enabled");
        } catch (Throwable error) {
            XposedBridge.log("TikTokRootMod: MIUI Home icon hook: " + error);
        }
    }

    private static boolean matches(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof LauncherActivityInfo && TARGET.equals(
                    ((LauncherActivityInfo) arg).getComponentName().getPackageName())) return true;
            if (arg instanceof String && TARGET.equals(arg)) return true;
        }
        return false;
    }

    private static Drawable iconDrawable() {
        if (icon == null) icon = BitmapFactory.decodeFile(ICON_PATH);
        if (icon == null) return null;
        return new BitmapDrawable(android.content.res.Resources.getSystem(), icon);
    }
}
