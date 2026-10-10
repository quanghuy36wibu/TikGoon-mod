package dev.tiktokrootmod;

import android.util.DisplayMetrics;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Feed-button visibility and long-press guards. */
final class FeedButtonHooks {
    private static final int LIVE = 1;
    private static final int SEARCH = 2;
    private static final int FOLLOW = 3;
    private static final int SAVE = 4;
    private static final int TAKO = 5;

    private static final WeakHashMap<View, Boolean> HANDLED = new WeakHashMap<>();
    private static final Map<View, Integer> HIDDEN = new WeakHashMap<>();
    private static int hits;
    private static int dumps;
    private static int blocked;

    private FeedButtonHooks() {}

    static void install() {
        if (Config.HIDE_FEED_LIVE || Config.HIDE_FEED_SEARCH || Config.HIDE_FEED_FOLLOW
                || Config.HIDE_FEED_SAVE || Config.HIDE_TAKO) {
            XC_MethodHook inspectHook = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof View)) return;
                    final View view = (View) param.thisObject;
                    String description = ownDescription(view);
                    if (description.isEmpty() && Config.HIDE_TAKO && view instanceof TextView)
                        description = ((TextView) view).getText() == null ? "" : ((TextView) view).getText().toString();
                    // Inspect even unlabeled views: a useful accessibility label may be on a parent.
                    if (description.length() > 80) return;
                    final String value = description.toLowerCase(Locale.ROOT);
                    view.post(() -> inspect(view, value, true));
                }
            };
            XposedBridge.hookAllMethods(View.class, "onAttachedToWindow", inspectHook);
            XposedBridge.hookAllMethods(View.class, "setContentDescription", inspectHook);
            if (Config.HIDE_TAKO) XposedBridge.hookAllMethods(TextView.class, "setText", inspectHook);

            // TikTok may reset visibility during a rebind/re-layout. Keep explicitly hidden views hidden.
            XposedBridge.hookAllMethods(View.class, "setVisibility", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof View) || param.args == null || param.args.length == 0) return;
                    Integer required = HIDDEN.get((View) param.thisObject);
                    if (required != null && ((Integer) param.args[0]).intValue() != required.intValue()) param.args[0] = required;
                }
            });
        }

        if (Config.NO_LONG_LIKE || Config.NO_LONG_SHARE) {
            XposedBridge.hookAllMethods(View.class, "performLongClick", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof View && blockLongPress((View) param.thisObject))
                        param.setResult(Boolean.TRUE);
                }
            });
        }
        HookLog.log("TikTokRootMod: feed button hooks enabled");
    }

    private static String ownDescription(View view) {
        CharSequence description = view.getContentDescription();
        return description == null ? "" : description.toString();
    }

    /** Walk through child and parent labels; don't stop at an unrelated child label. */
    private static boolean blockLongPress(View view) {
        DisplayMetrics metrics = view.getResources().getDisplayMetrics();
        int[] position = new int[2];
        view.getLocationOnScreen(position);
        float x = position[0] + view.getWidth() / 2f;
        float y = position[1] + view.getHeight() / 2f;
        if (x < metrics.widthPixels * 0.68f || y < metrics.heightPixels * 0.18f || y > metrics.heightPixels * 0.92f)
            return false;

        View current = view;
        for (int depth = 0; depth < 6 && current != null; depth++) {
            String value = ownDescription(current).toLowerCase(Locale.ROOT);
            if (current instanceof TextView) {
                CharSequence text = ((TextView) current).getText();
                if (text != null) value += " " + text.toString().toLowerCase(Locale.ROOT);
            }
            boolean like = Config.NO_LONG_LIKE && containsAny(value,
                    "like", "thích", "repost", "đăng lại", "heart", "tim", "thả tim");
            boolean share = Config.NO_LONG_SHARE && containsAny(value,
                    "share", "chia sẻ", "gửi", "send");
            if (like || share) {
                if (blocked++ < 20) HookLog.log("TikTokRootMod: blocked long press on '" + value.trim() + "'");
                return true;
            }
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return false;
    }

    private static boolean containsAny(String text, String... tokens) {
        for (String token : tokens) if (text.contains(token)) return true;
        return false;
    }

    private static void inspect(View view, String description, boolean retry) {
        if (!view.isAttachedToWindow()) return;
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
        boolean topBar = y < height * 0.16f;
        boolean rightColumn = x > width * 0.76f && y > height * 0.18f && y < height * 0.93f;

        String label = description == null ? "" : description;
        // The app frequently puts accessibility text on a parent rather than the icon view.
        View parent = view.getParent() instanceof View ? (View) view.getParent() : null;
        for (int depth = 0; depth < 4 && parent != null; depth++) {
            String parentLabel = ownDescription(parent).toLowerCase(Locale.ROOT);
            if (!parentLabel.isEmpty() && parentLabel.length() <= 80) label += " " + parentLabel;
            if (parent instanceof TextView) {
                CharSequence text = ((TextView) parent).getText();
                if (text != null && text.length() <= 80) label += " " + text.toString().toLowerCase(Locale.ROOT);
            }
            parent = parent.getParent() instanceof View ? (View) parent.getParent() : null;
        }
        label = label.toLowerCase(Locale.ROOT).trim();
        if (label.isEmpty()) return;

        if (Config.LOG_ENABLED && dumps < 80 && (topBar || rightColumn)) {
            dumps++;
            HookLog.log("TikTokRootMod: button candidate '" + label + "' " + view.getClass().getSimpleName()
                    + " at " + (int) x + "," + (int) y + " size " + view.getWidth() + "x" + view.getHeight());
        }

        int kind = 0;
        if (Config.HIDE_TAKO && label.contains("tako")) kind = TAKO;
        else if (Config.HIDE_FEED_LIVE && topBar && x < width * 0.34f
                && containsAny(label, "live", "trực tiếp")) kind = LIVE;
        else if (Config.HIDE_FEED_SEARCH && topBar && x > width * 0.66f
                && containsAny(label, "search", "tìm kiếm", "tìm kiếm video")) kind = SEARCH;
        else if (Config.HIDE_FEED_FOLLOW && rightColumn && view.getWidth() <= width * 0.18f
                && containsAny(label, "follow", "theo dõi", "follow user", "thêm bạn")) kind = FOLLOW;
        else if (Config.HIDE_FEED_SAVE && rightColumn
                && containsAny(label, "favorite", "yêu thích", "bookmark", "save", "lưu", "đã lưu", "saved", "collect", "收藏")) kind = SAVE;
        if (kind == 0 || HANDLED.containsKey(view)) return;

        View target = view;
        if (kind == SAVE || kind == FOLLOW) {
            View current = view;
            for (int depth = 0; depth < 4; depth++) {
                if (!(current.getParent() instanceof View)) break;
                View p = (View) current.getParent();
                if (p.getWidth() > width * 0.22f || p.getHeight() > height * 0.16f) break;
                current = p;
            }
            target = current;
        }
        HANDLED.put(view, true);
        HANDLED.put(target, true);
        final int mode = (kind == LIVE || kind == SEARCH) ? View.INVISIBLE : View.GONE;
        HIDDEN.put(target, mode);
        if (hits++ < 30) HookLog.log("TikTokRootMod: hid feed button kind=" + kind + " '" + label + "' "
                + target.getClass().getSimpleName() + " " + target.getWidth() + "x" + target.getHeight());
        target.setVisibility(mode);
    }
}
