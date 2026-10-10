package dev.tiktokrootmod;

import android.util.DisplayMetrics;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Ẩn các nút trong feed theo mô tả (contentDescription) và vị trí trên màn hình, không phụ thuộc tên lớp
 * bị làm rối của TikTok: nút LIVE (trên trái), tìm kiếm (trên phải), dấu + follow và nút lưu (cột phải).
 * Ý tưởng lấy từ danh sách bản vá Morphe/Metra; mã được viết lại độc lập.
 */
final class FeedButtonHooks {
    private static final int LIVE = 1;
    private static final int SEARCH = 2;
    private static final int FOLLOW = 3;
    private static final int SAVE = 4;
    private static final int TAKO = 5;

    private static final WeakHashMap<View, Boolean> HANDLED = new WeakHashMap<>();
    private static int hits;
    private static int dumps;
    private static int blocked;

    private FeedButtonHooks() {}

    static void install() {
        if (Config.HIDE_FEED_LIVE || Config.HIDE_FEED_SEARCH || Config.HIDE_FEED_FOLLOW || Config.HIDE_FEED_SAVE
                || Config.HIDE_TAKO) {
            XC_MethodHook hook = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof View)) return;
                    final View view = (View) param.thisObject;
                    CharSequence description = view.getContentDescription();
                    if ((description == null || description.length() == 0) && Config.HIDE_TAKO && view instanceof TextView)
                        description = ((TextView) view).getText();           // bong bóng Tako thường là chữ
                    if (description == null || description.length() == 0 || description.length() > 40) return;
                    if (HANDLED.containsKey(view)) return;
                    final String value = description.toString().toLowerCase(Locale.ROOT);
                    view.post(() -> inspect(view, value, true));
                }
            };
            XposedBridge.hookAllMethods(View.class, "onAttachedToWindow", hook);
            XposedBridge.hookAllMethods(View.class, "setContentDescription", hook);
            if (Config.HIDE_TAKO) XposedBridge.hookAllMethods(TextView.class, "setText", hook);
        }
        if (Config.NO_LONG_LIKE || Config.NO_LONG_SHARE) {
            XposedBridge.hookAllMethods(View.class, "performLongClick", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof View && blockLongPress((View) param.thisObject))
                        param.setResult(Boolean.TRUE);                         // coi như đã xử lý, TikTok không nhận
                }
            });
        }
        HookLog.log("TikTokRootMod: feed button hooks enabled");
    }

    /** Giữ lâu nút tim (repost) hoặc nút chia sẻ (chia sẻ nhanh) trong cột phải thì bỏ qua. */
    private static boolean blockLongPress(View view) {
        DisplayMetrics metrics = view.getResources().getDisplayMetrics();
        int[] position = new int[2];
        view.getLocationOnScreen(position);
        float x = position[0] + view.getWidth() / 2f;
        float y = position[1] + view.getHeight() / 2f;
        if (x < metrics.widthPixels * 0.75f || y < metrics.heightPixels * 0.2f || y > metrics.heightPixels * 0.9f)
            return false;
        View current = view;
        for (int depth = 0; depth < 3 && current != null; depth++) {      // mô tả có thể nằm ở khối cha
            CharSequence description = current.getContentDescription();
            if (description != null && description.length() > 0) {
                String value = description.toString().toLowerCase(Locale.ROOT);
                boolean like = Config.NO_LONG_LIKE && (value.contains("like") || value.contains("thích"));
                boolean share = Config.NO_LONG_SHARE && (value.contains("share") || value.contains("chia sẻ"));
                if (like || share) {
                    if (blocked++ < 5)
                        HookLog.log("TikTokRootMod: blocked long press on '" + value + "'");
                    return true;
                }
                return false;
            }
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return false;
    }

    private static void inspect(View view, String description, boolean retry) {
        if (HANDLED.containsKey(view) || !view.isAttachedToWindow()) return;
        if (view.getWidth() == 0 && retry) {
            view.postDelayed(() -> inspect(view, description, false), 400);
            return;
        }
        DisplayMetrics metrics = view.getResources().getDisplayMetrics();
        int[] position = new int[2];
        view.getLocationOnScreen(position);
        float x = position[0] + view.getWidth() / 2f;
        float y = position[1] + view.getHeight() / 2f;
        float width = metrics.widthPixels;
        float height = metrics.heightPixels;
        boolean topBar = y < height * 0.14f;
        boolean rightColumn = x > width * 0.8f && y > height * 0.2f && y < height * 0.9f;

        if (Config.LOG_ENABLED && dumps < 40 && (topBar || rightColumn)) {
            dumps++;
            HookLog.log("TikTokRootMod: button '" + description + "' " + view.getClass().getSimpleName()
                    + " at " + (int) x + "," + (int) y + " size " + view.getWidth() + "x" + view.getHeight());
        }

        int kind = 0;
        if (Config.HIDE_TAKO && description.contains("tako")) kind = TAKO;
        else if (Config.HIDE_FEED_LIVE && topBar && x < width * 0.3f
                && (description.equals("live") || description.startsWith("live ") || description.contains("trực tiếp")))
            kind = LIVE;
        else if (Config.HIDE_FEED_SEARCH && topBar && x > width * 0.7f
                && (description.contains("search") || description.contains("tìm kiếm")))
            kind = SEARCH;
        else if (Config.HIDE_FEED_FOLLOW && rightColumn && view.getWidth() <= width * 0.12f
                && (description.contains("follow") || description.contains("theo dõi")))
            kind = FOLLOW;
        else if (Config.HIDE_FEED_SAVE && rightColumn
                && (description.contains("favorite") || description.contains("yêu thích")
                || description.contains("bookmark") || description.contains("save") || description.contains("lưu")))
            kind = SAVE;
        if (kind == 0) return;

        View target = view;
        if (kind == SAVE) {                                   // gồm cả biểu tượng lẫn số đếm bên dưới
            View current = view;
            for (int depth = 0; depth < 3; depth++) {
                if (!(current.getParent() instanceof View)) break;
                View parent = (View) current.getParent();
                if (parent.getWidth() > width * 0.2f || parent.getHeight() > height * 0.14f) break;
                current = parent;
            }
            target = current;
        }
        HANDLED.put(view, true);
        HANDLED.put(target, true);
        final int mode = (kind == LIVE || kind == SEARCH) ? View.INVISIBLE : View.GONE;   // thanh trên giữ nguyên bố cục
        if (hits++ < 8)
            HookLog.log("TikTokRootMod: hid feed button kind=" + kind + " '" + description + "' "
                    + target.getClass().getSimpleName() + " " + target.getWidth() + "x" + target.getHeight());
        target.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (v.getVisibility() != mode) v.post(() -> v.setVisibility(mode));
        });
        target.setVisibility(mode);
    }
}
