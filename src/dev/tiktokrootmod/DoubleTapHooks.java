package dev.tiktokrootmod;

import android.app.Activity;
import android.util.DisplayMetrics;
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
 *
 * Lớp dự phòng: nếu TikTok tự đếm chạm bằng code riêng (không qua GestureDetector), ta chặn luôn lần chạm thứ hai
 * trong vòng 0,65 giây ở vùng video (trừ cột nút bên phải, thanh trên và dưới). Lần chạm thứ nhất vẫn chạy như thường.
 */
final class DoubleTapHooks {
    private static int logged;
    private static int touchLogs;
    private static int swallowLogs;
    private static int blockLogs;
    private static int traceCount;
    private static int stackLogs;
    private static final java.util.HashSet<String> TRACED = new java.util.HashSet<>();
    private static final String[] CALLBACKS = {"onDown", "onSingleTapUp", "onSingleTapConfirmed", "onDoubleTap",
            "onDoubleTapEvent", "onLongPress", "onShowPress"};

    private DoubleTapHooks() {}

    static void install() {
        XposedBridge.hookAllMethods(GestureDetector.class, "setOnDoubleTapListener", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length != 1 || !(param.args[0] instanceof GestureDetector.OnDoubleTapListener)) return;
                GestureDetector.OnDoubleTapListener original = (GestureDetector.OnDoubleTapListener) param.args[0];
                if (original instanceof Blocker) return;
                // Bỏ qua listener của chính Android (ví dụ ScaleGestureDetector dùng cho zoom), chỉ bọc listener của app.
                if (original.getClass().getName().startsWith("android.view.")) return;
                trace(original.getClass());
                param.args[0] = new Blocker(original);
                if (logged++ < 5)
                    HookLog.log("TikTokRootMod: double-tap listener wrapped: " + original.getClass().getName());
            }
        });
        XposedBridge.hookAllMethods(Activity.class, "dispatchTouchEvent", new XC_MethodHook() {
            private long lastDown;
            private float lastX;
            private float lastY;
            private boolean swallowing;

            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length != 1 || !(param.args[0] instanceof MotionEvent)) return;
                MotionEvent event = (MotionEvent) param.args[0];
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    DisplayMetrics metrics = ((Activity) param.thisObject).getResources().getDisplayMetrics();
                    float density = metrics.density;
                    boolean inVideoArea = event.getX() < metrics.widthPixels - 72 * density
                            && event.getY() > 80 * density
                            && event.getY() < metrics.heightPixels - 140 * density;
                    long time = event.getEventTime();
                    // Cửa sổ rộng hơn (0,65 giây, 150dp) vì TikTok có thể dùng ngưỡng chạm đúp riêng, dài hơn của Android.
                    boolean second = lastDown != 0 && time - lastDown < 650
                            && Math.hypot(event.getX() - lastX, event.getY() - lastY) < 150 * density;
                    swallowing = inVideoArea && second;
                    if (Config.LOG_ENABLED && touchLogs < 20) {
                        touchLogs++;
                        HookLog.log("TikTokRootMod: touch down " + (int) event.getX() + "," + (int) event.getY()
                                + " video=" + inVideoArea + " second=" + second
                                + " activity=" + param.thisObject.getClass().getSimpleName());
                    }
                    if (Config.LOG_ENABLED && swallowing && swallowLogs++ < 10) HookLog.log("TikTokRootMod: swallowed second tap");
                    lastDown = time;      // chạm liên tục: chỉ lần đầu qua, các lần sau trong 0,65 giây đều bị nuốt
                    lastX = event.getX();
                    lastY = event.getY();
                }
                if (swallowing) {
                    param.setResult(true);                 // nuốt cả chuỗi DOWN/MOVE/UP của lần chạm thứ hai
                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) swallowing = false;
                }
            }
        });
        HookLog.log("TikTokRootMod: double-tap like disabled");
    }

    /** Chẩn đoán: ghi lại các hàm cử chỉ mà listener của TikTok được gọi (chỉ khi bật nhật ký). */
    private static void trace(Class<?> type) {
        if (!Config.LOG_ENABLED || !TRACED.add(type.getName())) return;
        for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
            final String name = method.getName();
            boolean wanted = false;
            for (String callback : CALLBACKS) if (callback.equals(name)) wanted = true;
            if (!wanted || method.getParameterTypes().length != 1) continue;
            try {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (traceCount++ < 60)
                            HookLog.log("TikTokRootMod: " + type.getSimpleName() + "." + name + " called");
                        if (stackLogs < 6 && (name.equals("onSingleTapUp") || name.equals("onSingleTapConfirmed")
                                || name.equals("onDoubleTap"))) {
                            stackLogs++;
                            StringBuilder stack = new StringBuilder();
                            StackTraceElement[] frames = new Throwable().getStackTrace();
                            for (int i = 2; i < Math.min(frames.length, 12); i++)
                                stack.append("  ").append(frames[i]).append('\n');
                            HookLog.log("TikTokRootMod: stack of " + name + ":\n" + stack);
                        }
                    }
                });
            } catch (Throwable ignored) { }
        }
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
            if (blockLogs++ < 5)
                HookLog.log("TikTokRootMod: blocked onDoubleTap of " + base.getClass().getName());
            return false;
        }

        @Override public boolean onDoubleTapEvent(MotionEvent event) {
            return false;
        }
    }
}
