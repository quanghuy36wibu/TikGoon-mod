package dev.tiktokrootmod;

import android.view.GestureDetector;
import android.view.MotionEvent;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Tắt "chạm 2 lần để thả tim".
 *
 * TikTok (và androidx GestureDetectorCompat) nhận chạm đúp qua GestureDetector.OnDoubleTapListener.
 * Ta bọc listener đó: chạm một lần vẫn chạy bình thường (onSingleTapConfirmed), còn chạm đúp
 * (onDoubleTap, onDoubleTapEvent) bị bỏ qua nên không thả tim.
 */
final class DoubleTapHooks {
    private static int logged;

    private DoubleTapHooks() {}

    static void install() {
        XposedBridge.hookAllMethods(GestureDetector.class, "setOnDoubleTapListener", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length != 1 || !(param.args[0] instanceof GestureDetector.OnDoubleTapListener)) return;
                GestureDetector.OnDoubleTapListener original = (GestureDetector.OnDoubleTapListener) param.args[0];
                if (original instanceof Blocker) return;
                param.args[0] = new Blocker(original);
                if (logged++ < 5)
                    XposedBridge.log("TikTokRootMod: double-tap listener wrapped: " + original.getClass().getName());
            }
        });
        XposedBridge.log("TikTokRootMod: double-tap like disabled");
    }

    private static final class Blocker implements GestureDetector.OnDoubleTapListener {
        private final GestureDetector.OnDoubleTapListener base;

        Blocker(GestureDetector.OnDoubleTapListener base) {
            this.base = base;
        }

        @Override public boolean onSingleTapConfirmed(MotionEvent event) {
            return base.onSingleTapConfirmed(event);
        }

        @Override public boolean onDoubleTap(MotionEvent event) {
            return false;
        }

        @Override public boolean onDoubleTapEvent(MotionEvent event) {
            return false;
        }
    }
}
