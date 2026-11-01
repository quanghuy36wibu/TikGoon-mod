package dev.tiktokrootmod;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Safely enables TikTok's profile-background gates.
 *
 * Do not broadly hook every primitive-returning method with a String argument:
 * on obfuscated builds this can intercept unrelated settings calls and crash
 * profile screens. If the exact gate is unavailable, skip this feature.
 */
final class ProfileBackgroundHooks {
    private ProfileBackgroundHooks() {}

    static void install(ClassLoader loader) {
        try {
            XposedHelpers.findAndHookMethod("X.032Z", loader, "LJIIJJI",
                    int.class, int.class, String.class, boolean.class, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (param.args == null || param.args.length < 3 ||
                                        !(param.args[2] instanceof String)) return;
                                String key = (String) param.args[2];
                                if ("profile_bg_in_allow_list".equals(key)) {
                                    param.setResult(1);
                                    HookLog.log("TikTokRootMod: profile background allow-list gate opened");
                                } else if ("profile_bg_enable_consumption_group".equals(key)) {
                                    param.setResult(3);
                                    HookLog.log("TikTokRootMod: profile background UI gate opened");
                                }
                            } catch (Throwable error) {
                                HookLog.log("TikTokRootMod: profile background gate callback failed: " + error);
                            }
                        }
                    });
            HookLog.log("TikTokRootMod: hooked profile background gate in X.032Z");
        } catch (Throwable error) {
            HookLog.log("TikTokRootMod: profile background disabled safely; exact gate unavailable: " + error);
        }
        // Intentionally no broad legacy fallback hooks. A version-specific exact
        // gate is required to avoid hooking unrelated obfuscated settings methods.
    }
}
