package dev.tiktokrootmod;

import android.os.BaseBundle;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class MessageHooks {
    private MessageHooks() {}

    static void install(ClassLoader loader) {
        if (Config.UNLIMITED_SHARE_RECIPIENTS) {
            try {
                XposedHelpers.findAndHookMethod(BaseBundle.class, "getInt", String.class, int.class,
                        new XC_MethodHook() {
                            @Override protected void afterHookedMethod(MethodHookParam param) {
                                if ("forward_limit".equals(param.args[0])) param.setResult(1000);
                            }
                        });
            } catch (Throwable error) { XposedBridge.log("TikTokRootMod: recipient bundle hook: " + error); }
            Class<?> type = XposedHelpers.findClassIfExists(
                    "com.ss.android.ugc.aweme.im.sharepanel.api.experiment.ForwardLimitSetting", loader);
            if (type != null) for (Method method : type.getDeclaredMethods()) {
                if (!"LIZ".equals(method.getName()) || method.getReturnType() != int.class ||
                        method.getParameterTypes().length != 1 || method.getParameterTypes()[0] != int.class) continue;
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) { param.setResult(1000); }
                    });
                } catch (Throwable error) { XposedBridge.log("TikTokRootMod: recipient limit: " + error); }
            }
        }
        if (Config.UNLIMITED_PINNED_CHATS) {
            Class<?> type = XposedHelpers.findClassIfExists(
                    "com.ss.android.ugc.aweme.im.chatlist.impl.IMChatListImpl", loader);
            if (type != null) for (Method method : type.getDeclaredMethods()) {
                if (!("LJJIJIIJI".equals(method.getName()) || "LJJIJIIJIL".equals(method.getName())) ||
                        method.getReturnType() != Object.class || method.getParameterTypes().length != 3) continue;
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) { param.setResult(false); }
                    });
                } catch (Throwable error) { XposedBridge.log("TikTokRootMod: pinned chat guard: " + error); }
            }
        }
    }
}
