package dev.tiktokrootmod;

import android.util.DisplayMetrics;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Ẩn nút quà/thưởng nổi của TikTok (ví dụ "Nhấp ngay có thưởng" ở góc trái trên).
 *
 * Không phụ thuộc tên lớp của TikTok: khi một TextView hoặc contentDescription có chữ kiểu "có thưởng",
 * ta tìm khối nhỏ chứa nó (nằm ở nửa trên màn hình) rồi ẩn đi và giữ ẩn nếu TikTok hiện lại.
 */
final class PromoHooks {
    private static final Pattern PROMO = Pattern.compile(
            "có thưởng|nhận thưởng|nhấp ngay|nhập ngay|kiếm tiền|claim (now|reward)|get reward|earn (reward|cash|coin)|rewards? (now|waiting)");
    private static final WeakHashMap<View, Boolean> HANDLED = new WeakHashMap<>();
    private static int logs;

    private PromoHooks() {}

    static void install() {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof View)) return;
                View view = (View) param.thisObject;
                CharSequence text = null;
                if (view instanceof TextView) text = ((TextView) view).getText();
                if ((text == null || text.length() == 0) && param.args.length == 1
                        && param.args[0] instanceof CharSequence) text = (CharSequence) param.args[0];
                inspect(view, text);
            }
        };
        XposedBridge.hookAllMethods(TextView.class, "setText", hook);
        XposedBridge.hookAllMethods(View.class, "setContentDescription", hook);
        HookLog.log("TikTokRootMod: promo widget hiding enabled");
    }

    private static void inspect(View view, CharSequence text) {
        if (text == null || text.length() == 0 || text.length() > 60 || HANDLED.containsKey(view)) return;
        final String value = text.toString().toLowerCase(Locale.ROOT);
        if (!PROMO.matcher(value).find()) return;
        view.post(() -> hide(view, value, true));
    }

    private static void hide(View view, String value, boolean retry) {
        if (HANDLED.containsKey(view) || !view.isAttachedToWindow()) return;
        if (view.getWidth() == 0 && retry) {                       // chưa đo xong thì thử lại một lần
            view.postDelayed(() -> hide(view, value, false), 400);
            return;
        }
        DisplayMetrics metrics = view.getResources().getDisplayMetrics();
        View target = null;
        View current = view;
        for (int depth = 0; depth < 8 && current != null; depth++) {
            if (current.getWidth() > metrics.widthPixels * 0.45f || current.getHeight() > metrics.heightPixels * 0.30f)
                break;
            target = current;
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        if (target == null) return;
        int[] position = new int[2];
        target.getLocationOnScreen(position);
        if (position[1] + target.getHeight() / 2f > metrics.heightPixels * 0.5f) return;   // chỉ nửa trên màn hình

        HANDLED.put(view, true);
        HANDLED.put(target, true);
        if (logs++ < 5)
            HookLog.log("TikTokRootMod: hid promo widget " + target.getClass().getSimpleName() + " "
                    + target.getWidth() + "x" + target.getHeight() + " text=" + value);
        final View hidden = target;
        hidden.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (v.getVisibility() != View.GONE) v.post(() -> v.setVisibility(View.GONE));   // TikTok hiện lại thì ẩn tiếp
        });
        hidden.setVisibility(View.GONE);
    }
}
