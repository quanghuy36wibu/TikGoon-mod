package dev.tiktokrootmod;

import android.animation.ObjectAnimator;
import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;

import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

final class AppearanceHooks {
    // Resource IDs from TikTok 47.0.3, com.ss.android.ugc.trill.
    private static final int WIDGETS = 0x7f0a9ff0;
    private static final int STICKERS = 0x7f0a9c3b;
    private static final int BOTTOM_GRADIENT = 0x7f0a33c7;
    private static final int TITLE_SHADOW = 0x7f0a882a;
    private static final int Z38 = 0x7f0a8686;
    private static final int OK6 = 0x7f0a4e4c;
    private static final int CN3 = 0x7f0a0e8e;
    private static final WeakHashMap<View, ObjectAnimator> SHIFTS = new WeakHashMap<>();
    private static boolean loggedMinimal;
    private static boolean loggedDim;

    private AppearanceHooks() {}

    static void install() {
        if (!Config.MINIMAL_UI && !Config.ANTI_BURNOUT && "default".equals(Config.FONT_FAMILY)) return;
        if (Config.MINIMAL_UI || Config.ANTI_BURNOUT) {
            XposedBridge.hookAllMethods(View.class, "onAttachedToWindow", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    View view = (View) param.thisObject;
                    if (Config.MINIMAL_UI && minimal(view.getId())) view.setVisibility(View.GONE);
                    if (Config.ANTI_BURNOUT && dim(view.getId())) {
                        view.setAlpha(Math.min(view.getAlpha(), 0.35f));
                        if (view.getId() == WIDGETS && !Config.MINIMAL_UI) shift(view);
                    }
                }
            });
            XposedBridge.hookAllMethods(View.class, "setVisibility", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    View view = (View) param.thisObject;
                    if (Config.MINIMAL_UI && minimal(view.getId())) {
                        param.args[0] = View.GONE;
                        if (!loggedMinimal) {
                            loggedMinimal = true;
                            XposedBridge.log("TikTokRootMod: minimal feed overlay applied");
                        }
                    }
                }
            });
            XposedBridge.hookAllMethods(View.class, "setAlpha", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    View view = (View) param.thisObject;
                    if (Config.ANTI_BURNOUT && dim(view.getId())) {
                        float requested = (Float) param.args[0];
                        if (requested > 0.35f) {
                            param.args[0] = 0.35f;
                            if (!loggedDim) {
                                loggedDim = true;
                                XposedBridge.log("TikTokRootMod: anti-burnout opacity applied");
                            }
                        }
                    }
                }
            });
        }
        if (!"default".equals(Config.FONT_FAMILY)) {
            final Typeface face = "serif".equals(Config.FONT_FAMILY) ? Typeface.SERIF :
                    "monospace".equals(Config.FONT_FAMILY) ? Typeface.MONOSPACE : Typeface.DEFAULT;
            XposedBridge.hookAllMethods(TextView.class, "setTypeface", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length > 0 && param.args[0] instanceof Typeface) param.args[0] = face;
                }
            });
        }
    }

    private static boolean minimal(int id) {
        return id == WIDGETS || id == STICKERS || id == BOTTOM_GRADIENT || id == TITLE_SHADOW;
    }

    private static boolean dim(int id) {
        return id == WIDGETS || id == STICKERS || id == BOTTOM_GRADIENT ||
                id == TITLE_SHADOW || id == Z38 || id == OK6 || id == CN3;
    }

    private static void shift(View view) {
        if (SHIFTS.containsKey(view)) return;
        float distance = 4f * view.getResources().getDisplayMetrics().density;
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, "translationY", -distance, distance);
        animator.setDuration(40000);
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setRepeatMode(ObjectAnimator.REVERSE);
        SHIFTS.put(view, animator);
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View attached) { }
            @Override public void onViewDetachedFromWindow(View detached) {
                ObjectAnimator old = SHIFTS.remove(detached);
                if (old != null) old.cancel();
                detached.removeOnAttachStateChangeListener(this);
            }
        });
        animator.start();
    }
}
