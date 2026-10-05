package dev.tiktokrootmod;

import java.lang.reflect.Method;
import android.view.View;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class VideoHooks {
    private static volatile boolean loggedFade;
    private VideoHooks() {}

    static void install(ClassLoader loader) {
        if (!Config.ALWAYS_SHOW_SEEKBAR) return;
        final int seekBarId = 0x7f0a9c2a; // TikTok 47.0.3: id/video_seek_bar
        final Class<?> seekView = XposedHelpers.findClassIfExists("X.06l9", loader);
        XposedBridge.hookAllMethods(View.class, "setVisibility", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                View view = (View) param.thisObject;
                if ((view.getId() == seekBarId || (seekView != null && seekView.isInstance(view))) &&
                        (Integer) param.args[0] != View.VISIBLE)
                    param.args[0] = View.VISIBLE;
            }
        });
        XposedBridge.hookAllMethods(View.class, "setAlpha", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                View view = (View) param.thisObject;
                if (view.getId() != seekBarId && (seekView == null || !seekView.isInstance(view))) return;
                if ((Float) param.args[0] < 1f) {
                    param.args[0] = 1f;
                    if (!loggedFade) {
                        loggedFade = true;
                        XposedBridge.log("TikTokRootMod: blocked seekbar alpha fade");
                    }
                }
            }
        });
        XposedBridge.hookAllMethods(View.class, "onAttachedToWindow", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                View view = (View) param.thisObject;
                if (view.getId() == seekBarId || (seekView != null && seekView.isInstance(view))) {
                    view.setVisibility(View.VISIBLE);
                    view.setAlpha(1f);
                }
            }
        });
        XposedBridge.log("TikTokRootMod: hooked video_seek_bar view visibility");
        Class<?> mainPageSeek = XposedHelpers.findClassIfExists(
                "com.bytedance.tiktok.homepage.mainpagefragment.assem.MainPageSeekAssem", loader);
        if (mainPageSeek != null) {
            try {
                XposedHelpers.findAndHookMethod(mainPageSeek, "Ax1", boolean.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) { param.args[0] = false; }
                });
                XposedBridge.log("TikTokRootMod: hooked MainPageSeekAssem hideSeekBar");
            } catch (Throwable error) { XposedBridge.log("TikTokRootMod: main seekbar controller: " + error); }
        }
        Class<?> seekDelegate = XposedHelpers.findClassIfExists("X.06nm", loader);
        if (seekDelegate != null) {
            try {
                XposedHelpers.findAndHookMethod(seekDelegate, "LJFF", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(null); // TikTok animates the seekbar alpha to zero here.
                    }
                });
                XposedBridge.log("TikTokRootMod: blocked seekbar fade-out");
            } catch (Throwable error) { XposedBridge.log("TikTokRootMod: seekbar fade-out: " + error); }
        }
        if (seekView != null) {
            try {
                XposedHelpers.findAndHookMethod(seekView, "setSeekBarShowType", int.class,
                        new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam param) {
                                int mode = (Integer) param.args[0];
                                if (mode == 3 || mode == 4) param.args[0] = 0;
                            }
                        });
                XposedBridge.log("TikTokRootMod: hooked actual seekbar show type");
            } catch (Throwable error) { XposedBridge.log("TikTokRootMod: seekbar show type: " + error); }
            XposedBridge.hookAllMethods(seekView, "onAttachedToWindow", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    View view = (View) param.thisObject;
                    view.setAlpha(1f);
                    try { XposedHelpers.callMethod(view, "setSeekBarShowType", 0); }
                    catch (Throwable error) { XposedBridge.log("TikTokRootMod: initial seekbar mode: " + error); }
                }
            });
        }
        Class<?> aweme = XposedHelpers.findClassIfExists("com.ss.android.ugc.aweme.feed.model.Aweme", loader);
        Class<?> controller = XposedHelpers.findClassIfExists("X.06jj", loader);
        if (aweme != null && controller != null) {
            int hooked = 0;
            for (Method method : controller.getDeclaredMethods()) {
                Class<?>[] args = method.getParameterTypes();
                if (method.getReturnType() != boolean.class || args.length != 1 || args[0] != aweme) continue;
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) { param.setResult(true); }
                    });
                    hooked++;
                } catch (Throwable error) { XposedBridge.log("TikTokRootMod: seekbar gate: " + error); }
            }
            XposedBridge.log("TikTokRootMod: seekbar gates: " + hooked);
        }
        Class<?> wrapper = XposedHelpers.findClassIfExists("X.06hm", loader);
        if (wrapper != null) {
            for (Method method : wrapper.getDeclaredMethods()) {
                Class<?>[] args = method.getParameterTypes();
                if (!"setSeekBarShowType".equals(method.getName()) || method.getReturnType() != void.class ||
                        args.length != 1 || args[0] != int.class) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        int mode = (Integer) param.args[0];
                        if (mode == 3 || mode == 4) param.args[0] = 0;
                    }
                });
            }
        }
    }
}
