package dev.tiktokrootmod;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Replaces large black TikTok backgrounds; content and video remain untouched. */
final class ThemeHooks {
    static final String IMAGE_PATH = "/data/user/0/com.ss.android.ugc.trill/files/tiktokrootmod_theme.jpg";
    private static final WeakHashMap<View, Boolean> THEMED = new WeakHashMap<>();
    private static final WeakHashMap<FrameLayout, Boolean> COMMENT_THEMED = new WeakHashMap<>();
    /** Các khung bao quanh khung bình luận: tạm thời không phủ nền để ảnh không tràn ra cả màn hình. */
    private static final WeakHashMap<View, Boolean> SUPPRESSED = new WeakHashMap<>();
    private static final WeakHashMap<FrameLayout, Boolean> SUPPRESS_ACTIVE = new WeakHashMap<>();
    private static Bitmap wallpaper;
    private static long imageLastModified = Long.MIN_VALUE;
    private static long imageLength = -1L;
    private static int themedCount;
    private static int commentLogs;
    private static final String INPUT_HINT = "(?i).*(thêm bình luận|add comment|add a comment|tambahkan komentar|"
            + "añade un comentario|ajouter un commentaire|kommentar hinzufügen|添加评论|添加評論|コメントを追加|댓글 추가).*";
    /** Tiêu đề khung bình luận, ví dụ "1.883 bình luận" hoặc "1,883 comments". */
    private static final String COMMENT_TITLE = ".*[0-9].*(comments?|bình luận|komentar|comentarios?|commentaires?|kommentare?|комментар|评论|評論|コメント|댓글|ความคิดเห็น|yorum).*";
    private ThemeHooks() {}

    static void install() {
        if (Config.THEME_COLOR_OPACITY == 0 && Config.THEME_IMAGE_OPACITY == 0 &&
                Config.THEME_VIDEO_OPACITY == 0) return;
        XposedBridge.hookAllMethods(View.class, "onAttachedToWindow", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                View view = (View) param.thisObject;
                consider(view);
                themeComment(view);
            }
        });
        XposedBridge.hookAllMethods(View.class, "setBackground", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                consider((View) param.thisObject);
            }
        });
        XposedBridge.hookAllMethods(View.class, "setBackgroundColor", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                consider((View) param.thisObject);
            }
        });
        XposedBridge.hookAllMethods(TextView.class, "setText", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                themeComment((View) param.thisObject);
            }
        });
        XposedBridge.log("TikTokRootMod: dark-background theme hook enabled");
    }

    private static void themeComment(View view) {
        if (!(view instanceof TextView)) return;
        CharSequence text = ((TextView) view).getText();
        if (text == null || text.length() > 80 ||
                !text.toString().toLowerCase(java.util.Locale.ROOT).matches(COMMENT_TITLE)) return;
        view.post(() -> {
            StringBuilder chain = new StringBuilder();
            // Ưu tiên khung bo góc (GradientDrawable) như bản gốc; không có mới dùng khung nền phẳng.
            FrameLayout panel = findCommentPanel(view, false, chain);
            if (panel == null) panel = findCommentPanel(view, true, null);
            if (panel == null) {
                if (commentLogs++ < 3)
                    XposedBridge.log("TikTokRootMod: comment title found but no panel matched: " + chain);
                return;
            }
            suppressAncestors(panel);
            applyCommentLayer(panel);
        });
    }

    private static FrameLayout findCommentPanel(View start, boolean allowColor, StringBuilder chain) {
        int width = start.getResources().getDisplayMetrics().widthPixels;
        int height = start.getResources().getDisplayMetrics().heightPixels;
        View current = start;
        for (int depth = 0; depth < 12 && current != null; depth++) {
            Drawable background = current.getBackground();
            if (chain != null && commentLogs < 3) chain.append(current.getClass().getSimpleName()).append('[')
                    .append(background == null ? "-" : background.getClass().getSimpleName()).append(' ')
                    .append(current.getWidth()).append('x').append(current.getHeight()).append("] > ");
            if (current instanceof FrameLayout &&
                    (background instanceof GradientDrawable || (allowColor && background instanceof ColorDrawable)) &&
                    current.getWidth() >= width * 0.7f && current.getHeight() >= height * 0.2f)
                return (FrameLayout) current;
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return null;
    }

    /**
     * Trong lúc khung bình luận mở, các khung bao quanh (thường là cả màn hình) không được phủ ảnh,
     * nếu không ảnh sẽ tràn xuống cả vùng dưới ô nhập. Đóng bình luận thì trả lại như cũ.
     */
    private static void suppressAncestors(FrameLayout panel) {
        if (SUPPRESS_ACTIVE.containsKey(panel)) return;
        SUPPRESS_ACTIVE.put(panel, true);
        final List<View> ancestors = new ArrayList<>();
        View parent = panel.getParent() instanceof View ? (View) panel.getParent() : null;
        for (int depth = 0; parent != null && depth < 30; depth++) {
            SUPPRESSED.put(parent, true);                 // đặt cờ trước để hook setBackground không phủ lại
            ancestors.add(parent);
            Drawable background = parent.getBackground();
            if (background instanceof BlackThemeDrawable) {
                THEMED.remove(parent);
                parent.setBackgroundColor(((BlackThemeDrawable) background).baseColor);
            }
            parent = parent.getParent() instanceof View ? (View) parent.getParent() : null;
        }
        panel.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { }
            @Override public void onViewDetachedFromWindow(View v) {
                v.removeOnAttachStateChangeListener(this);
                SUPPRESS_ACTIVE.remove(panel);
                for (View ancestor : ancestors) {
                    SUPPRESSED.remove(ancestor);
                    consider(ancestor);
                }
            }
        });
    }

    /** Đáy của hàng "Thêm bình luận..." so với khung bình luận, hoặc -1 nếu không tìm thấy. */
    private static int commentInputBottom(ViewGroup panel) {
        TextView input = findCommentInput(panel, 0);
        if (input == null) return -1;
        int screenHeight = panel.getResources().getDisplayMetrics().heightPixels;
        View container = input;
        View current = input;
        while (current.getParent() instanceof View && current.getParent() != panel) {
            current = (View) current.getParent();
            if (current.getWidth() >= panel.getWidth() * 0.9f && current.getHeight() <= screenHeight * 0.3f)
                container = current;
        }
        int[] panelPos = new int[2];
        int[] containerPos = new int[2];
        panel.getLocationOnScreen(panelPos);
        container.getLocationOnScreen(containerPos);
        return containerPos[1] - panelPos[1] + container.getHeight();
    }

    private static TextView findCommentInput(View view, int depth) {
        if (depth > 14) return null;
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            CharSequence value = text.getHint();
            if (value == null || value.length() == 0) value = text.getText();
            if (value != null && value.length() <= 60 &&
                    value.toString().toLowerCase(java.util.Locale.ROOT).matches(INPUT_HINT)) return text;
            return null;
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            TextView found = findCommentInput(group.getChildAt(i), depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    /** Giới hạn ảnh/màu chỉ phủ phần trên ô nhập bình luận, không tràn xuống dưới. */
    private static void clipOverlays(ViewGroup panel, List<View> overlays) {
        try {
            int bottom = commentInputBottom(panel);
            int height = bottom > 0 && bottom < panel.getHeight() ? bottom : ViewGroup.LayoutParams.MATCH_PARENT;
            for (View overlay : overlays) {
                ViewGroup.LayoutParams params = overlay.getLayoutParams();
                if (params != null && params.height != height) {
                    params.height = height;
                    overlay.setLayoutParams(params);
                }
            }
        } catch (Throwable error) {
            XposedBridge.log("TikTokRootMod: clip comment overlay: " + error);
        }
    }

    private static void applyCommentLayer(FrameLayout panel) {
        if (COMMENT_THEMED.containsKey(panel)) return;
        COMMENT_THEMED.put(panel, true);
        panel.setClipToOutline(true);
        final List<View> overlays = new ArrayList<>();
        Bitmap image = loadImage();
        if (image != null && Config.THEME_IMAGE_OPACITY > 0) {
            ImageView wallpaperView = new ImageView(panel.getContext());
            wallpaperView.setImageBitmap(image);
            wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            wallpaperView.setAlpha(Config.THEME_IMAGE_OPACITY / 100f);
            wallpaperView.setClickable(false);
            panel.addView(wallpaperView, 0, new FrameLayout.LayoutParams(-1, -1));
            overlays.add(wallpaperView);
        }
        if (Config.THEME_VIDEO_OPACITY > 0) VideoBackground.attach(panel, true);
        if (Config.THEME_COLOR_OPACITY > 0) {
            View color = new View(panel.getContext());
            color.setClickable(false);
            color.setAlpha(Config.THEME_COLOR_OPACITY / 100f);
            if (Config.THEME_RAINBOW)
                color.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        new int[]{0xffff004e, 0xffffa500, 0xffffff00, 0xff00cc66, 0xff00aaff, 0xff9900ff}));
            else {
                try { color.setBackgroundColor(Color.parseColor(Config.THEME_COLOR)); }
                catch (IllegalArgumentException ignored) { color.setBackgroundColor(0xffff2d55); }
            }
            panel.addView(color, Math.min(2, panel.getChildCount()), new FrameLayout.LayoutParams(-1, -1));
            overlays.add(color);
        }
        if (!overlays.isEmpty()) {
            panel.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (b - t != ob - ot || r - l != or - ol) clipOverlays(panel, overlays);
            });
            panel.postDelayed(() -> clipOverlays(panel, overlays), 300);
            panel.postDelayed(() -> clipOverlays(panel, overlays), 900);
        }
        XposedBridge.log("TikTokRootMod: comment panel background themed");
    }

    private static void consider(View view) {
        if (!(view instanceof ViewGroup) || THEMED.containsKey(view) || SUPPRESSED.containsKey(view)) return;
        Drawable background = view.getBackground();
        if (!(background instanceof ColorDrawable) || !themable(((ColorDrawable) background).getColor())) return;
        view.post(() -> {
            try {
                if (THEMED.containsKey(view) || SUPPRESSED.containsKey(view) || !view.isAttachedToWindow()) return;
                int screenWidth = view.getResources().getDisplayMetrics().widthPixels;
                int screenHeight = view.getResources().getDisplayMetrics().heightPixels;
                if (view.getWidth() < screenWidth * 0.7f || view.getHeight() < screenHeight * 0.20f) return;
                Drawable current = view.getBackground();
                if (!(current instanceof ColorDrawable) ||
                        !themable(((ColorDrawable) current).getColor())) return;
                Bitmap image = loadImage();
                if (Config.THEME_IMAGE_OPACITY > 0 && image == null && Config.THEME_COLOR_OPACITY == 0 &&
                        Config.THEME_VIDEO_OPACITY == 0) return;
                THEMED.put(view, true);
                view.setBackground(new BlackThemeDrawable(image, ((ColorDrawable) current).getColor()));
                String className = view.getClass().getName();
                if (Config.THEME_VIDEO_OPACITY > 0 &&
                        (view instanceof FrameLayout || view instanceof RelativeLayout ||
                                "X.05q6".equals(className) || "X.02Gn".equals(className) ||
                                className.contains("ConstraintLayout")))
                    VideoBackground.attach((ViewGroup) view);
                if (++themedCount <= 8) {
                    StringBuilder hierarchy = new StringBuilder();
                    for (Class<?> type = view.getClass(); type != null && type != View.class;
                         type = type.getSuperclass()) hierarchy.append(type.getSimpleName()).append(" > ");
                    XposedBridge.log("TikTokRootMod: replaced dark background " + hierarchy +
                            view.getWidth() + "x" + view.getHeight());
                }
            } catch (Throwable error) {
                XposedBridge.log("TikTokRootMod: background theme: " + error);
            }
        });
    }

    /** Nền tối như trước; nếu bật "giao diện sáng" thì nền trắng cũng được thay. */
    private static boolean themable(int color) {
        if (isDark(color)) return true;
        return Config.THEME_LIGHT && Color.alpha(color) == 255 && Color.red(color) >= 245 &&
                Color.green(color) >= 245 && Color.blue(color) >= 245;
    }

    private static boolean isDark(int color) {
        return Color.alpha(color) == 255 && Color.red(color) <= 42 &&
                Color.green(color) <= 42 && Color.blue(color) <= 42;
    }

    private static synchronized Bitmap loadImage() {
        if (Config.THEME_IMAGE_OPACITY == 0) return wallpaper;
        File file = new File(IMAGE_PATH);
        if (!file.isFile()) {
            if (wallpaper != null) {
                try { wallpaper.recycle(); } catch (Throwable ignored) { }
                wallpaper = null;
            }
            imageLastModified = Long.MIN_VALUE;
            imageLength = -1L;
            return null;
        }
        long modified = file.lastModified();
        long length = file.length();
        if (wallpaper != null && modified == imageLastModified && length == imageLength)
            return wallpaper;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(IMAGE_PATH, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 2048)
            options.inSampleSize *= 2;
        Bitmap replacement = BitmapFactory.decodeFile(IMAGE_PATH, options);
        if (replacement == null) return null;
        Bitmap old = wallpaper;
        wallpaper = replacement;
        imageLastModified = modified;
        imageLength = length;
        if (old != null && old != replacement) {
            try { old.recycle(); } catch (Throwable ignored) { }
        }
        return wallpaper;
    }

    private static final class BlackThemeDrawable extends Drawable {
        private final Bitmap image;
        private final int baseColor;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        BlackThemeDrawable(Bitmap image, int baseColor) {
            this.image = image;
            this.baseColor = baseColor;
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            canvas.drawColor(baseColor);
            if (image != null && Config.THEME_IMAGE_OPACITY > 0) {
                float scale = Math.max((float) bounds.width() / image.getWidth(),
                        (float) bounds.height() / image.getHeight());
                int sourceWidth = Math.min(image.getWidth(), (int) (bounds.width() / scale));
                int sourceHeight = Math.min(image.getHeight(), (int) (bounds.height() / scale));
                int left = (image.getWidth() - sourceWidth) / 2;
                int top = (image.getHeight() - sourceHeight) / 2;
                paint.setShader(null);
                paint.setColor(Color.WHITE);
                paint.setAlpha(Math.round(Config.THEME_IMAGE_OPACITY * 2.55f));
                canvas.drawBitmap(image, new Rect(left, top, left + sourceWidth, top + sourceHeight), bounds, paint);
            }
            if (Config.THEME_COLOR_OPACITY > 0) {
                if (Config.THEME_RAINBOW) {
                    paint.setColor(Color.WHITE);
                    paint.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                            new int[]{0xffff004e, 0xffffa500, 0xffffff00, 0xff00cc66, 0xff00aaff, 0xff9900ff},
                            null, Shader.TileMode.CLAMP));
                } else {
                    paint.setShader(null);
                    try { paint.setColor(Color.parseColor(Config.THEME_COLOR)); }
                    catch (IllegalArgumentException ignored) { paint.setColor(0xffff2d55); }
                }
                paint.setAlpha(Math.round(Config.THEME_COLOR_OPACITY * 2.55f));
                canvas.drawRect(bounds, paint);
                paint.setShader(null);
            }
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
