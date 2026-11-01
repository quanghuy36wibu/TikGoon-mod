package dev.tiktokrootmod;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Enable TikTok's own profile background controls through its settings gates. */
final class ProfileBackgroundHooks {
    private static final String[] SETTINGS_CLASSES = {
            "com.bytedance.ies.abmock.SettingsManager", "X.02z4", "X.033O", "X.04ME",
            "X.03Pn", "X.04Xi", "X.03eN", "X.03PD", "X.033T", "X.035D",
            "X.03er", "X.04MH", "X.06Ot", "X.06uJ"
    };

    private ProfileBackgroundHooks() {}

    static void install(ClassLoader loader) {
        try {
            XposedHelpers.findAndHookMethod("X.032Z", loader, "LJIIJJI",
                    int.class, int.class, String.class, boolean.class, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            String key = (String) param.args[2];
                            if ("profile_bg_in_allow_list".equals(key)) {
                                param.setResult(1);
                                XposedBridge.log("TikTokRootMod: profile background allow-list gate opened");
                            } else if ("profile_bg_enable_consumption_group".equals(key)) {
                                param.setResult(3);
                                XposedBridge.log("TikTokRootMod: profile background UI gate opened");
                            }
                        }
                    });
            XposedBridge.log("TikTokRootMod: hooked profile background gate in X.032Z");
        } catch (Throwable error) {
            XposedBridge.log("TikTokRootMod: direct profile background gate unavailable: " + error);
        }
        int count = 0;
        for (String name : SETTINGS_CLASSES) {
            Class<?> type = XposedHelpers.findClassIfExists(name, loader);
            if (type == null) continue;
            for (Method method : type.getDeclaredMethods()) {
                if (Modifier.isAbstract(method.getModifiers()) || Modifier.isNative(method.getModifiers())) continue;
                Class<?> result = method.getReturnType();
                if (result != int.class && result != Integer.class &&
                        result != boolean.class && result != Boolean.class) continue;
                int stringIndex = -1;
                Class<?>[] parameters = method.getParameterTypes();
                for (int i = 0; i < parameters.length; i++)
                    if (parameters[i] == String.class) { stringIndex = i; break; }
                if (stringIndex < 0) continue;
                final int keyIndex = stringIndex;
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (param.args.length <= keyIndex || !(param.args[keyIndex] instanceof String)) return;
                            String key = (String) param.args[keyIndex];
                            if ("profile_bg_in_allow_list".equals(key)) {
                                if (result == boolean.class || result == Boolean.class) param.setResult(Boolean.TRUE);
                                else param.setResult(Integer.valueOf(1));
                            } else if ("profile_bg_enable_consumption_group".equals(key)) {
                                if (result == boolean.class || result == Boolean.class) param.setResult(Boolean.TRUE);
                                else param.setResult(Integer.valueOf(3));
                            }
                        }
                    });
                    count++;
                } catch (Throwable error) {
                    XposedBridge.log("TikTokRootMod: profile background hook skipped: " + error);
                }
            }
        }
        XposedBridge.log("TikTokRootMod: profile background setting methods hooked: " + count);
    }
}
