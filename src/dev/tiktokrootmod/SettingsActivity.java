package dev.tiktokrootmod;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

public final class SettingsActivity extends Activity {
    // Bảng màu Material 3 tối, lấy theo LSPosed
    private static final int BG = 0xFF13151D;
    private static final int CARD = 0xFF262933;
    private static final int CARD_HI = 0xFF343A52;
    private static final int PRIMARY = 0xFFB9C3FF;
    private static final int ON_PRIMARY = 0xFF1C2B5E;
    private static final int TEXT = 0xFFE2E2EC;
    private static final int TEXT_SUB = 0xFF9496A5;
    private static final int OUTLINE = 0xFF454859;
    private static final int OK = 0xFF7DDC95;
    private static final int BAD = 0xFFFF8A80;
    private static final int WARN = 0xFFFFD166;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final ExecutorService SAVES = Executors.newSingleThreadExecutor();
    private static final ExecutorService ROOT = Executors.newSingleThreadExecutor();
    private static final String TIKTOK = "com.ss.android.ugc.trill";
    private static final String STATUS_FILE = "/data/user/0/" + TIKTOK + "/files/tiktokrootmod_status";
    private static final int PICK_THEME_IMAGE = 701;
    private static final int PICK_LAUNCHER_ICON = 702;
    private static final int PICK_THEME_VIDEO = 703;
    private SharedPreferences prefs;
    private EditText likes;
    private EditText views;
    private EditText publishTime;
    private EditText filterWords;
    private EditText regionIso;
    private EditText regionOperator;
    private EditText regionName;
    private EditText themeColor;

    private FrameLayout content;
    private final ArrayList<ScrollView> pages = new ArrayList<>();
    private final ArrayList<FrameLayout> tabPills = new ArrayList<>();
    private final ArrayList<IconView> tabIcons = new ArrayList<>();
    private final ArrayList<TextView> tabLabels = new ArrayList<>();

    // Trạng thái trực tiếp
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int tick;
    private int currentTab = -1;
    private int transitionId;
    private final java.util.HashMap<String, SeekBar> opacityBars = new java.util.HashMap<>();
    private final Runnable restartTask = this::restartTikTok;
    private volatile boolean rootChecking;
    private StatusRow stModule, stRoot, stTikTok, stHook, stConfig;
    private TextView liveUpdated, summaryIcon, summaryTitle, summarySub;
    private String rootManager = "";
    private int rootState;            // 0 đang kiểm tra, 1 đã cấp, 2 không có
    private String rootVersion = "", rootInfo = "", tiktokPid = "", statusPid = "";
    private long statusLoaded;
    private boolean statusExists, statusOk;

    /** Được Xposed hook trong tiến trình của chính module để trả về số API. Mặc định 0 = chưa kích hoạt. */
    static int xposedApiVersion() { return 0; }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            renderAll();
            tick++;
            if (tick % 5 == 0 && prefs.getBoolean("live_monitor", true)) refreshRoot();
            handler.postDelayed(this, 2000);
        }
    };

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        tick = 0;
        ticker.run();
        refreshRoot();
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
        if (currentTab >= 0) {          // dừng animation đang dở, chỉ giữ lại đúng trang hiện tại
            transitionId++;
            showOnly(currentTab);
        }
    }

    @Override protected void onCreate(Bundle state) {
        setTheme(android.R.style.Theme_DeviceDefault_NoActionBar);
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        prefs = getSharedPreferences(Config.PREFS, 0);
        if (!prefs.contains("hide_ads")) prefs.edit().putBoolean("hide_ads", true).commit();

        rootManager = detectRootManager();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        buildOverview(page("TikGoon"));
        buildAppearance(page("Giao diện"));
        buildRegion(page("Vùng và bộ lọc"));
        buildAdvanced(page("Nâng cao"));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(BG);
        String[] labels = {"Tổng quan", "Giao diện", "Vùng", "Nâng cao"};
        for (int i = 0; i < labels.length; i++) addTab(nav, i, labels[i]);
        root.addView(nav, new LinearLayout.LayoutParams(MATCH, WRAP));

        setContentView(root);
        selectTab(0);
    }

    // ---------------------------------------------------------------- trang

    private void buildOverview(LinearLayout p) {
        statusCard(p);
        section(p, "Trạng thái trực tiếp");
        LinearLayout live = card(p);
        stModule = statusRow(live, "Module LSPosed", null);
        stRoot = statusRow(live, "Quyền root", this::refreshRoot);
        stTikTok = statusRow(live, "TikTok", null);
        stHook = statusRow(live, "Hook trong TikTok", null);
        stConfig = statusRow(live, "Cấu hình đồng bộ", null);
        LinearLayout monitor = toggle(live, "Theo dõi trực tiếp", "Quét root mỗi 10 giây", "live_monitor", true, false);
        liveUpdated = (TextView) monitor.getTag();
        toggle(live, "Tự khởi động lại TikTok", "Sau khi đổi cài đặt xong (đợi 3 giây không đổi thêm), dùng root",
                "auto_restart", false, false);
        action(live, "Khởi động lại TikTok ngay", "Buộc dừng rồi mở lại bằng root", this::confirmRestart);
        note(live, "Sau khi đổi cài đặt, buộc dừng và mở lại TikTok. Chạm vào dòng Quyền root để kiểm tra lại ngay. KernelSU có thể hiện thông báo mỗi lần quét.");

        section(p, "Lọc feed");
        LinearLayout feed = card(p);
        toggle(feed, "Ẩn quảng cáo trong feed", null, "hide_ads", true);
        toggle(feed, "Ẩn livestream", null, "hide_lives", false);
        toggle(feed, "Ẩn bài ảnh", null, "hide_photos", false);
        toggle(feed, "Ẩn story", null, "hide_stories", false);
        toggle(feed, "Ẩn mini-series", null, "hide_series", false);
        toggle(feed, "Ẩn nội dung trả phí", null, "hide_paid", false);

        section(p, "Chung");
        LinearLayout general = card(p);
        toggle(general, "Làm sạch link được copy", "Xóa tham số theo dõi", "clean_links", true);
        toggle(general, "Giả lập vùng Kazakhstan", "Đổi vùng ở tab Vùng", "spoof_region", true);
        toggle(general, "Cho phép chụp/quay màn hình", null, "allow_screenshots", true);
        toggle(general, "Mở tải ảnh nền hồ sơ", null, "profile_background", true);
    }

    private void buildAppearance(LinearLayout p) {
        section(p, "Video");
        LinearLayout video = card(p);
        toggle(video, "Luôn hiện thanh tua video", null, "always_show_seekbar", false);
        toggle(video, "Giao diện tối giản", "Ẩn một số lớp phủ video", "minimal_ui", false);
        toggle(video, "Giảm lưu ảnh OLED", "Làm mờ, dịch chuyển lớp phủ", "anti_burnout", false);
        note(video, "Khi bật cả hai, Tối giản ẩn lớp phủ nên hiệu ứng làm mờ chỉ thấy rõ khi tắt Tối giản.");
        LinearLayout fontRow = action(video, "Font TikTok", prefs.getString("font_family", "default"), null);
        TextView fontSub = (TextView) fontRow.getTag();
        fontRow.setOnClickListener(v -> dialog().setTitle("Font TikTok")
                .setItems(new String[]{"Mặc định", "Serif", "Monospace"}, (d, which) -> {
                    String family = new String[]{"default", "serif", "monospace"}[which];
                    prefs.edit().putString("font_family", family).apply();
                    fontSub.setText(family);
                    syncSettings();
                }).show());

        section(p, "Màu giao diện");
        LinearLayout color = card(p);
        note(color, "Màu, ảnh hoặc video thay các vùng nền tối, kể cả khung bình luận. Video bài đăng và nút bấm vẫn ở phía trước.");
        themeColor = textInput(color, "Màu HEX (#RRGGBB)", "theme_color", "#FF2D55");
        action(color, "Chọn màu mẫu", "Hồng, xanh dương, tím, xanh lá, cam, trắng", () -> dialog().setTitle("Màu giao diện")
                .setItems(new String[]{"Hồng", "Xanh dương", "Tím", "Xanh lá", "Cam", "Trắng"}, (d, which) -> {
                    themeColor.setText(new String[]{"#FF2D55", "#2196F3", "#9C27B0", "#4CAF50", "#FF9800", "#FFFFFF"}[which]);
                    saveThemeColor();
                }).show());
        action(color, "Lưu màu HEX", null, this::saveThemeColor);
        opacity(color, "Độ mờ màu", "theme_color_opacity", 0);
        toggle(color, "Dải màu rainbow trên nền đen", null, "theme_rainbow", false);

        section(p, "Ảnh nền");
        LinearLayout image = card(p);
        action(image, "Chọn ảnh thay nền đen", null, () -> pickImage(PICK_THEME_IMAGE));
        opacity(image, "Độ mờ ảnh", "theme_image_opacity", 0);
        action(image, "Xóa ảnh giao diện", null, this::clearThemeImage);

        section(p, "Video nền");
        LinearLayout bgVideo = card(p);
        action(bgVideo, "Chọn video thay nền tối", null, this::pickVideo);
        opacity(bgVideo, "Độ mờ video nền", "theme_video_opacity", 0);
        note(bgVideo, "Video nền phát lặp, tắt tiếng. Nên chọn MP4 ngắn để giảm pin và tải máy.");
        action(bgVideo, "Xóa video nền", null, this::clearThemeVideo);

        section(p, "Biểu tượng TikTok trên MIUI Home");
        LinearLayout icon = card(p);
        note(icon, "Đổi icon của TikTok đang hiện trên màn hình chính và ngăn ứng dụng; có thể trở về logo gốc.");
        action(icon, "Chọn ảnh đổi icon TikTok", null, () -> pickImage(PICK_LAUNCHER_ICON));
        action(icon, "Về logo TikTok gốc", null, this::resetLauncherIcon);
    }

    private void buildRegion(LinearLayout p) {
        section(p, "Vùng giả lập");
        LinearLayout region = card(p);
        action(region, "Chọn từ 191 quốc gia/vùng", "Tự điền mã ISO và nhà mạng", this::showRegionPicker);
        regionIso = textInput(region, "Mã quốc gia ISO (ví dụ: kz)", "region_iso", "kz");
        regionOperator = textInput(region, "Mã nhà mạng MCC/MNC (ví dụ: 40101)", "region_operator", "40101");
        regionName = textInput(region, "Tên nhà mạng", "region_operator_name", "Beeline");

        section(p, "Bộ lọc video");
        LinearLayout filter = card(p);
        likes = number(filter, "Lượt thích tối thiểu (0 = tắt)", "min_likes");
        views = number(filter, "Lượt xem tối thiểu (0 = tắt)", "min_views");
        publishTime = number(filter, "Thời điểm đăng tối thiểu, Unix giây (0 = tắt)", "min_publish_time");
        TextView wordsLabel = text("Từ khóa cần ẩn (ngăn cách bằng dấu phẩy)", 14, TEXT_SUB);
        wordsLabel.setPadding(dp(20), dp(12), dp(20), dp(6));
        filter.addView(wordsLabel);
        filterWords = styledInput(filter);
        filterWords.setSingleLine(false);
        filterWords.setMinLines(2);
        filterWords.setGravity(Gravity.TOP | Gravity.START);
        filterWords.setText(prefs.getString("filter_words", ""));

        TextView save = text("Lưu cài đặt", 16, ON_PRIMARY);
        save.setTypeface(Typeface.DEFAULT_BOLD);
        save.setGravity(Gravity.CENTER);
        save.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33000000), round(PRIMARY, 28), null));
        save.setOnClickListener(v -> saveFields());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, dp(56));
        lp.setMargins(dp(16), dp(8), dp(16), dp(8));
        p.addView(save, lp);
    }

    private void buildAdvanced(LinearLayout p) {
        section(p, "Tải media");
        LinearLayout media = card(p);
        toggle(media, "Bỏ watermark khi tải video", "Dùng nút tải sẵn có của TikTok", "remove_download_watermark", false);

        section(p, "Tin nhắn");
        LinearLayout msg = card(p);
        toggle(msg, "Tăng giới hạn người nhận khi chia sẻ", null, "unlimited_share_recipients", false);
        toggle(msg, "Bỏ giới hạn ghim chat", null, "unlimited_pinned_chats", false);

        section(p, "LIVE");
        LinearLayout live = card(p);
        toggle(live, "Tắt tự dịch bình luận LIVE", null, "disable_live_auto_translate", true);

        LinearLayout foot = card(p);
        note(foot, "Chưa hỗ trợ tải ảnh/âm thanh/sticker, chọn chất lượng và tự gửi streak. Giao diện cần thử trên TikTok 47.0.3.");
    }

    // ------------------------------------------------------- điều hướng dưới

    private void addTab(LinearLayout nav, int index, String label) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(0, dp(12), 0, dp(14));
        FrameLayout pill = new FrameLayout(this);
        IconView icon = new IconView(this, index);
        pill.addView(icon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        item.addView(pill, new LinearLayout.LayoutParams(dp(64), dp(32)));
        TextView name = text(label, 12, TEXT_SUB);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(4), 0, 0);
        item.addView(name, new LinearLayout.LayoutParams(WRAP, WRAP));
        item.setOnClickListener(v -> selectTab(index));
        nav.addView(item, new LinearLayout.LayoutParams(0, WRAP, 1f));
        tabPills.add(pill);
        tabIcons.add(icon);
        tabLabels.add(name);
    }

    private void selectTab(int selected) {
        for (int i = 0; i < pages.size(); i++) {
            boolean on = i == selected;
            tabPills.get(i).setBackground(on ? round(CARD_HI, 16) : null);
            tabIcons.get(i).setColor(on ? TEXT : TEXT_SUB);
            tabLabels.get(i).setTextColor(on ? TEXT : TEXT_SUB);
            tabLabels.get(i).setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
        int previous = currentTab;
        if (previous == selected) return;
        currentTab = selected;
        final int id = ++transitionId;
        if (previous < 0) {                       // lần đầu mở app: không cần animation
            showOnly(selected);
            return;
        }

        // Kiểu "fade through": trang cũ mờ dần và trượt đi trước, rồi trang mới mới hiện ra.
        // Hai trang không bao giờ hiện cùng lúc nên chữ không bị chồng lên nhau.
        final ScrollView outgoing = pages.get(previous);
        final ScrollView incoming = pages.get(selected);
        final float direction = selected > previous ? 1f : -1f;
        final float shift = dp(24);

        for (int i = 0; i < pages.size(); i++) {
            ScrollView page = pages.get(i);
            page.animate().cancel();              // hủy animation cũ khi bấm tab liên tục
            if (page != outgoing) resetPage(page, View.GONE);
        }

        outgoing.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        outgoing.animate()
                .alpha(0f)
                .translationX(-direction * shift)
                .setDuration(90)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    if (id != transitionId) return;
                    resetPage(outgoing, View.GONE);
                    incoming.setAlpha(0f);
                    incoming.setTranslationX(direction * shift);
                    incoming.setVisibility(View.VISIBLE);
                    incoming.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                    incoming.animate()
                            .alpha(1f)
                            .translationX(0f)
                            .setDuration(210)
                            .setInterpolator(new DecelerateInterpolator(1.6f))
                            .withEndAction(() -> {
                                if (id == transitionId) resetPage(incoming, View.VISIBLE);
                            })
                            .start();
                })
                .start();
    }

    private void resetPage(ScrollView page, int visibility) {
        page.setAlpha(1f);
        page.setTranslationX(0f);
        page.setLayerType(View.LAYER_TYPE_NONE, null);
        page.setVisibility(visibility);
    }

    private void showOnly(int selected) {
        for (int i = 0; i < pages.size(); i++) {
            pages.get(i).animate().cancel();
            resetPage(pages.get(i), i == selected ? View.VISIBLE : View.GONE);
        }
    }

    /** Icon tab tự vẽ, không phụ thuộc icon hệ thống. */
    private static final class IconView extends View {
        private final int type;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private int color = TEXT_SUB;

        IconView(Context context, int type) {
            super(context);
            this.type = type;
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        void setColor(int value) {
            color = value;
            invalidate();
        }

        private void rect(Canvas c, float s, float l, float t, float r, float b) {
            box.set(l * s, t * s, r * s, b * s);
            c.drawRoundRect(box, 2.5f * s, 2.5f * s, paint);
        }

        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight()) / 24f;
            paint.setColor(color);
            paint.setStrokeWidth(2f * s);
            paint.setStyle(Paint.Style.STROKE);
            if (type == 0) {                       // Tổng quan: lưới 2x2
                rect(c, s, 3, 3, 10, 10);
                rect(c, s, 14, 3, 21, 10);
                rect(c, s, 3, 14, 10, 21);
                rect(c, s, 14, 14, 21, 21);
            } else if (type == 1) {                // Giao diện: bảng màu
                c.drawCircle(12 * s, 12 * s, 9 * s, paint);
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(8 * s, 10 * s, 1.5f * s, paint);
                c.drawCircle(12 * s, 7.5f * s, 1.5f * s, paint);
                c.drawCircle(16 * s, 10 * s, 1.5f * s, paint);
                c.drawCircle(9 * s, 15.5f * s, 1.5f * s, paint);
            } else if (type == 2) {                // Vùng: quả địa cầu
                c.drawCircle(12 * s, 12 * s, 9 * s, paint);
                box.set(8 * s, 3 * s, 16 * s, 21 * s);
                c.drawOval(box, paint);
                c.drawLine(3 * s, 12 * s, 21 * s, 12 * s, paint);
            } else {                               // Nâng cao: thanh trượt
                c.drawLine(3 * s, 6 * s, 21 * s, 6 * s, paint);
                c.drawLine(3 * s, 12 * s, 21 * s, 12 * s, paint);
                c.drawLine(3 * s, 18 * s, 21 * s, 18 * s, paint);
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(8 * s, 6 * s, 2.6f * s, paint);
                c.drawCircle(16 * s, 12 * s, 2.6f * s, paint);
                c.drawCircle(10 * s, 18 * s, 2.6f * s, paint);
            }
        }
    }

    // ------------------------------------------------------ thành phần giao diện

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private TextView text(String value, float sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception error) {
            return "0.8.6";
        }
    }

    private LinearLayout page(String title) {
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(false);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, 0, 0, dp(24));
        scroll.addView(column, new ScrollView.LayoutParams(MATCH, WRAP));
        TextView heading = text(title, 40, TEXT);
        heading.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        heading.setPadding(dp(32), dp(56), dp(32), dp(24));
        column.addView(heading);
        content.addView(scroll, new FrameLayout.LayoutParams(MATCH, MATCH));
        pages.add(scroll);
        return column;
    }

    private void statusCard(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackground(round(CARD_HI, 28));
        box.setPadding(dp(24), dp(22), dp(24), dp(22));

        summaryIcon = text("…", 18, CARD_HI);
        summaryIcon.setTypeface(Typeface.DEFAULT_BOLD);
        summaryIcon.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xFFC2C6E0);
        summaryIcon.setBackground(circle);
        box.addView(summaryIcon, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        summaryTitle = text("Đang kiểm tra", 20, 0xFFC9CCE4);
        summarySub = text("TikGoon " + versionName(), 14, TEXT_SUB);
        col.addView(summaryTitle);
        col.addView(summarySub);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        cp.setMargins(dp(20), 0, dp(12), 0);
        box.addView(col, cp);

        TextView badge = text("v" + versionName(), 14, ON_PRIMARY);
        badge.setPadding(dp(12), dp(5), dp(12), dp(5));
        badge.setBackground(round(0xFFC6CCF5, 8));
        box.addView(badge);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(dp(16), 0, dp(16), dp(12));
        parent.addView(box, lp);
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(round(CARD, 26));
        box.setClipToOutline(true);
        box.setPadding(0, dp(8), 0, dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(dp(16), 0, dp(16), dp(10));
        parent.addView(box, lp);
        return box;
    }

    private void section(LinearLayout parent, String label) {
        TextView view = text(label, 14, PRIMARY);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(dp(32), dp(18), dp(32), dp(8));
        parent.addView(view);
    }

    private void note(LinearLayout card, String value) {
        TextView view = text(value, 14, TEXT_SUB);
        view.setPadding(dp(20), dp(10), dp(20), dp(10));
        card.addView(view);
    }

    /** Một dòng tiêu đề + mô tả; TextView mô tả (nếu có) nằm trong tag của dòng. */
    private LinearLayout row(LinearLayout card, String title, String sub) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(12), dp(20), dp(12));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(title, 17, TEXT));
        if (sub != null) {
            TextView subView = text(sub, 14, TEXT_SUB);
            subView.setPadding(0, dp(2), 0, 0);
            col.addView(subView);
            row.setTag(subView);
        }
        row.addView(col, new LinearLayout.LayoutParams(0, WRAP, 1f));
        card.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
        return row;
    }

    private void pressable(View view, Runnable action) {
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x28FFFFFF), null,
                new ColorDrawable(Color.WHITE)));
        if (action != null) view.setOnClickListener(v -> action.run());
    }

    private LinearLayout action(LinearLayout card, String title, String sub, Runnable action) {
        LinearLayout row = row(card, title, sub);
        TextView chevron = text("›", 26, TEXT_SUB);
        row.addView(chevron);
        pressable(row, action);
        return row;
    }

    private LinearLayout toggle(LinearLayout card, String title, String sub, String key, boolean fallback) {
        return toggle(card, title, sub, key, fallback, true);
    }

    private LinearLayout toggle(LinearLayout card, String title, String sub, String key, boolean fallback,
                                boolean sync) {
        LinearLayout row = row(card, title, sub);
        Switch control = new Switch(this);
        int[][] states = {{android.R.attr.state_checked}, {}};
        control.setThumbTintList(new ColorStateList(states, new int[]{ON_PRIMARY, TEXT_SUB}));
        control.setTrackTintList(new ColorStateList(states, new int[]{PRIMARY, OUTLINE}));
        control.setChecked(prefs.getBoolean(key, fallback));
        control.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(key, checked).apply();
            if (sync) syncSettings();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.setMargins(dp(12), 0, 0, 0);
        row.addView(control, lp);
        pressable(row, control::toggle);
        return row;
    }

    // ------------------------------------------------ trạng thái trực tiếp

    private final class StatusRow {
        View dot;
        TextView sub;
    }

    private StatusRow statusRow(LinearLayout card, String title, Runnable onTap) {
        LinearLayout row = row(card, title, "Đang kiểm tra…");
        StatusRow status = new StatusRow();
        status.sub = (TextView) row.getTag();
        status.dot = new View(this);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(TEXT_SUB);
        status.dot.setBackground(oval);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(12), dp(12));
        lp.setMargins(dp(12), 0, 0, 0);
        row.addView(status.dot, lp);
        if (onTap != null) pressable(row, onTap);
        return status;
    }

    private void setStatus(StatusRow status, int color, String value) {
        status.sub.setText(value);
        ((GradientDrawable) status.dot.getBackground()).setColor(color);
    }

    private String detectRootManager() {
        String[][] known = {{"me.weishu.kernelsu", "KernelSU"}, {"com.rifsxd.ksunext", "KernelSU Next"},
                {"me.bmax.apatch", "APatch"}, {"com.topjohnwu.magisk", "Magisk"}};
        for (String[] item : known) {
            try {
                getPackageManager().getPackageInfo(item[0], 0);
                return item[1];
            } catch (Exception ignored) { }
        }
        return "";
    }

    private String clock(long millis) {
        return new SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()).format(new Date(millis));
    }

    /** Quét root ở nền: quyền root, TikTok có đang chạy không, và tệp trạng thái do hook ghi trong TikTok. */
    private void refreshRoot() {
        if (rootChecking) return;
        rootChecking = true;
        ROOT.execute(() -> {
            int state = 2;
            String version = "", info = "", pid = "", hookPid = "";
            long loaded = 0;
            boolean exists = false, ok = false;
            try {
                Process process = new ProcessBuilder("su", "-c",
                        "id -u; echo ---; pidof " + TIKTOK + "; echo ---; cat " + STATUS_FILE +
                                " 2>/dev/null; echo ---; su -v 2>/dev/null")
                        .redirectErrorStream(true).start();
                if (!process.waitFor(20, TimeUnit.SECONDS)) {
                    process.destroy();
                    info = "su không phản hồi";
                } else {
                    StringBuilder text = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) text.append(line).append('\n');
                    }
                    String[] part = text.toString().split("(?m)^---$", -1);
                    if (part[0].trim().equals("0")) {
                        state = 1;
                        if (part.length > 1) pid = part[1].trim().split("\\s+")[0];
                        if (part.length > 2) {
                            for (String line : part[2].trim().split("\n")) {
                                int eq = line.indexOf('=');
                                if (eq < 0) continue;
                                String key = line.substring(0, eq).trim();
                                String value = line.substring(eq + 1).trim();
                                exists = true;
                                if (key.equals("pid")) hookPid = value;
                                else if (key.equals("ok")) ok = value.equals("1");
                                else if (key.equals("loaded")) {
                                    try { loaded = Long.parseLong(value); } catch (NumberFormatException ignored) { }
                                }
                            }
                        }
                        if (part.length > 3) version = part[3].trim().split("\n")[0].trim();
                    } else {
                        String first = text.toString().trim();
                        info = first.isEmpty() ? "bị từ chối" : first.split("\n")[0];
                    }
                }
            } catch (Exception error) {
                info = "không tìm thấy su";
            }
            rootVersion = version;
            rootInfo = info;
            tiktokPid = pid;
            statusPid = hookPid;
            statusLoaded = loaded;
            statusExists = exists;
            statusOk = ok;
            rootState = state;
            rootChecking = false;
            runOnUiThread(this::renderAll);
        });
    }

    /** Đặt độ mờ trong cài đặt và cập nhật luôn thanh trượt trên màn hình. */
    private void setOpacity(String key, int value) {
        prefs.edit().putInt(key, value).apply();
        SeekBar bar = opacityBars.get(key);
        if (bar != null) bar.setProgress(value);
    }

    /** Sau khi chọn lại ảnh/video: nếu độ mờ đang 0% (do lần xóa trước) thì đặt lại 40% để nhìn thấy ngay. */
    private boolean restoreOpacity(String key) {
        if (prefs.getInt(key, 0) > 0) return false;
        setOpacity(key, 40);
        syncSettings();
        return true;
    }

    private boolean scheduleAutoRestart() {
        if (!prefs.getBoolean("auto_restart", false)) return false;
        handler.removeCallbacks(restartTask);
        handler.postDelayed(restartTask, 3000);
        return true;
    }

    private void confirmRestart() {
        dialog().setTitle("Khởi động lại TikTok?")
                .setMessage("TikTok sẽ bị buộc dừng rồi mở lại. Video đang tải lên hoặc đang soạn có thể bị mất.")
                .setPositiveButton("Khởi động lại", (d, w) -> restartTikTok())
                .setNegativeButton("Hủy", null).show();
    }

    /** Buộc dừng TikTok rồi mở lại bằng root để module nạp cài đặt mới. */
    private void restartTikTok() {
        Toast.makeText(this, "Đang khởi động lại TikTok…", Toast.LENGTH_SHORT).show();
        ROOT.execute(() -> {
            boolean success = false;
            try {
                Process process = new ProcessBuilder("su", "-c",
                        "am force-stop " + TIKTOK + "; sleep 1; monkey -p " + TIKTOK +
                                " -c android.intent.category.LAUNCHER 1")
                        .redirectErrorStream(true).start();
                if (process.waitFor(30, TimeUnit.SECONDS)) success = process.exitValue() == 0;
                else process.destroy();
            } catch (Exception ignored) { }
            final boolean done = success;
            runOnUiThread(() -> {
                Toast.makeText(this, done ? "Đã khởi động lại TikTok." :
                        "Không khởi động lại được. Kiểm tra quyền root và việc TikTok đã được cài.",
                        Toast.LENGTH_LONG).show();
                refreshRoot();
            });
        });
    }

    private boolean sameSettings(String a, String b) {
        try {
            JSONObject x = new JSONObject(new String(Base64.decode(a, Base64.DEFAULT), "UTF-8"));
            JSONObject y = new JSONObject(new String(Base64.decode(b, Base64.DEFAULT), "UTF-8"));
            if (x.length() != y.length()) return false;
            Iterator<String> keys = x.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!String.valueOf(x.opt(key)).equals(String.valueOf(y.opt(key)))) return false;
            }
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    private void renderAll() {
        if (stModule == null) return;
        String issue = null;

        // Module LSPosed
        int api = xposedApiVersion();
        boolean hookEvidence = rootState == 1 && statusExists;
        boolean moduleSeen = api > 0 || hookEvidence;
        if (api > 0) setStatus(stModule, OK, "Đang hoạt động · Xposed API " + api);
        else if (hookEvidence) setStatus(stModule, WARN,
                "Đã nạp được vào TikTok. Thêm TikGoon vào phạm vi LSPosed để hiện số API tại đây");
        else {
            setStatus(stModule, BAD, "Chưa phát hiện. Bật TikGoon trong LSPosed và chọn phạm vi TikTok");
            issue = "Chưa phát hiện module trong LSPosed";
        }

        // Root
        if (rootState == 1) {
            String text = "Đã cấp (uid=0)";
            if (!rootManager.isEmpty()) text += " · " + rootManager;
            if (!rootVersion.isEmpty()) text += " · su " + rootVersion;
            setStatus(stRoot, OK, text);
        } else if (rootState == 2) {
            setStatus(stRoot, BAD, "Chưa có quyền root" + (rootInfo.isEmpty() ? "" : " (" + rootInfo + ")")
                    + ". Cấp root cho TikGoon trong KernelSU/Magisk rồi chạm để thử lại");
            if (issue == null) issue = "Chưa có quyền root";
        } else {
            setStatus(stRoot, TEXT_SUB, "Đang kiểm tra…");
        }

        // TikTok
        boolean installed = false;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(TIKTOK, 0);
            installed = true;
            String v = info.versionName == null ? "?" : info.versionName;
            boolean match = v.startsWith("47.0.3");
            String run = rootState != 1 ? "" : (tiktokPid.isEmpty() ? " · đang tắt" : " · đang chạy (PID " + tiktokPid + ")");
            setStatus(stTikTok, match ? OK : WARN,
                    "Phiên bản " + v + (match ? "" : " (module viết cho 47.0.3, hook có thể không chạy)") + run);
            if (!match && issue == null) issue = "TikTok phiên bản " + v + " khác 47.0.3";
        } catch (Exception error) {
            setStatus(stTikTok, BAD, "Chưa cài TikTok (" + TIKTOK + ")");
            if (issue == null) issue = "Chưa cài TikTok";
        }

        // Hook trong TikTok
        boolean running = !tiktokPid.isEmpty();
        if (rootState != 1) {
            setStatus(stHook, TEXT_SUB, "Cần quyền root để kiểm tra");
        } else if (running && statusExists && tiktokPid.equals(statusPid)) {
            if (statusOk) setStatus(stHook, OK, "Đã nạp trong phiên TikTok hiện tại · " + clock(statusLoaded));
            else {
                setStatus(stHook, WARN, "Nạp hook bị lỗi lúc " + clock(statusLoaded) + ". Xem log LSPosed");
                if (issue == null) issue = "Hook trong TikTok bị lỗi";
            }
        } else if (running) {
            setStatus(stHook, BAD, "TikTok đang chạy nhưng chưa nạp hook. Kiểm tra phạm vi LSPosed, rồi buộc dừng và mở lại TikTok");
            if (issue == null) issue = "TikTok chưa nạp hook";
        } else if (statusExists) {
            setStatus(stHook, TEXT_SUB, "TikTok đang tắt · lần nạp gần nhất " + clock(statusLoaded)
                    + (statusOk ? "" : " (có lỗi)"));
        } else {
            setStatus(stHook, TEXT_SUB, "TikTok đang tắt · chưa từng nạp hook");
        }

        // Cấu hình đồng bộ
        String current = Settings.Global.getString(getContentResolver(), Config.SYSTEM_KEY);
        if (current == null || current.isEmpty()) {
            setStatus(stConfig, WARN, "Chưa đồng bộ. Cần quyền root; đổi một công tắc để lưu");
        } else {
            boolean same;
            try { same = sameSettings(current, encodedSettings()); } catch (Exception error) { same = false; }
            setStatus(stConfig, same ? OK : WARN, same ? "Đã đồng bộ, khớp cài đặt hiện tại"
                    : "Lệch với cài đặt trong app. Đổi một công tắc hoặc bấm Lưu để đồng bộ");
        }

        // Thẻ tổng quan
        boolean good = issue == null && rootState == 1 && moduleSeen && installed;
        if (rootState == 0) {
            summaryIcon.setText("…");
            summaryTitle.setText("Đang kiểm tra");
            summarySub.setText("TikGoon " + versionName());
        } else if (good) {
            summaryIcon.setText("✓");
            summaryTitle.setText("Đang hoạt động");
            summarySub.setText("Module, root và TikTok đều ổn");
        } else {
            summaryIcon.setText("!");
            summaryTitle.setText("Cần kiểm tra");
            summarySub.setText(issue == null ? "Xem chi tiết bên dưới" : issue);
        }
        if (liveUpdated != null)
            liveUpdated.setText("Cập nhật lúc " + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date())
                    + " · quét root mỗi 10 giây");
    }

    private void opacity(LinearLayout card, String label, String key, int fallback) {
        TextView value = text(label + ": " + prefs.getInt(key, fallback) + "%", 17, TEXT);
        value.setPadding(dp(20), dp(14), dp(20), dp(4));
        card.addView(value);
        SeekBar seek = new SeekBar(this);
        seek.setMax(80);
        opacityBars.put(key, seek);
        seek.setProgress(prefs.getInt(key, fallback));
        seek.setProgressTintList(ColorStateList.valueOf(PRIMARY));
        seek.setThumbTintList(ColorStateList.valueOf(PRIMARY));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(OUTLINE));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                value.setText(label + ": " + progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                prefs.edit().putInt(key, bar.getProgress()).apply();
                syncSettings();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(dp(8), 0, dp(8), dp(8));
        card.addView(seek, lp);
    }

    private EditText styledInput(LinearLayout card) {
        EditText input = new EditText(this);
        input.setTextColor(TEXT);
        input.setHintTextColor(TEXT_SUB);
        input.setTextSize(16);
        GradientDrawable bg = round(0xFF1C1E27, 16);
        bg.setStroke(dp(1), OUTLINE);
        input.setBackground(bg);
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        input.setOnFocusChangeListener((v, focused) ->
                bg.setStroke(dp(focused ? 2 : 1), focused ? PRIMARY : OUTLINE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(dp(16), 0, dp(16), dp(8));
        card.addView(input, lp);
        return input;
    }

    private EditText number(LinearLayout card, String label, String key) {
        TextView title = text(label, 14, TEXT_SUB);
        title.setPadding(dp(20), dp(12), dp(20), dp(6));
        card.addView(title);
        EditText input = styledInput(card);
        input.setHint("0");
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setText(Long.toString(prefs.getLong(key, 0)));
        return input;
    }

    private EditText textInput(LinearLayout card, String label, String key, String fallback) {
        TextView title = text(label, 14, TEXT_SUB);
        title.setPadding(dp(20), dp(12), dp(20), dp(6));
        card.addView(title);
        EditText input = styledInput(card);
        input.setSingleLine(true);
        input.setText(prefs.getString(key, fallback));
        return input;
    }

    // ------------------------------------------------------- logic gốc (giữ nguyên)

    private void saveThemeColor() {
        String color = themeColor.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);
        if (!color.matches("#[0-9A-F]{6}")) {
            Toast.makeText(this, "Nhập màu dạng #RRGGBB.", Toast.LENGTH_LONG).show();
            return;
        }
        prefs.edit().putString("theme_color", color).apply();
        syncSettings();
    }

    private void pickImage(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, requestCode);
    }

    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        startActivityForResult(intent, PICK_THEME_VIDEO);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == PICK_THEME_IMAGE) saveThemeImage(uri);
        else if (requestCode == PICK_LAUNCHER_ICON) saveLauncherIcon(uri);
        else if (requestCode == PICK_THEME_VIDEO) saveThemeVideo(uri);
    }

    private Bitmap decodeImage(Uri uri, int maxSide) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IllegalArgumentException("Invalid image");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > maxSide)
            options.inSampleSize *= 2;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            if (bitmap == null) throw new IllegalArgumentException("Invalid image");
            return bitmap;
        }
    }

    private void saveThemeImage(Uri uri) {
        SAVES.execute(() -> {
            boolean success = false;
            try {
                Bitmap bitmap = decodeImage(uri, 2048);
                File source = new File(getFilesDir(), "theme.jpg");
                try (FileOutputStream output = new FileOutputStream(source)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output))
                        throw new IllegalStateException("JPEG encode failed");
                }
                bitmap.recycle();
                String command = "cp /data/user/0/dev.tiktokrootmod/files/theme.jpg " +
                        ThemeHooks.IMAGE_PATH + " && chmod 644 " + ThemeHooks.IMAGE_PATH;
                Process process = new ProcessBuilder("su", "-c", command).start();
                success = process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0;
            } catch (Exception ignored) { }
            final boolean saved = success;
            runOnUiThread(() -> {
                if (!saved) {
                    Toast.makeText(this, "Không lưu được ảnh. Kiểm tra quyền root của module.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                boolean restored = restoreOpacity("theme_image_opacity");
                boolean auto = restored ? prefs.getBoolean("auto_restart", false) : scheduleAutoRestart();
                Toast.makeText(this, "Đã lưu ảnh" + (restored ? ", độ mờ đặt lại 40%" : "") +
                        (auto ? ". TikTok sẽ tự khởi động lại." : ". Mở lại TikTok để áp dụng."),
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private void clearThemeImage() {
        setOpacity("theme_image_opacity", 0);
        syncSettings();
        SAVES.execute(() -> {
            try { new ProcessBuilder("su", "-c", "rm -f " + ThemeHooks.IMAGE_PATH)
                    .start().waitFor(30, TimeUnit.SECONDS); }
            catch (Exception ignored) { }
        });
        Toast.makeText(this, "Đã xóa ảnh. Mở lại TikTok để áp dụng.", Toast.LENGTH_LONG).show();
    }

    private void saveThemeVideo(Uri uri) {
        SAVES.execute(() -> {
            boolean success = false;
            try {
                File source = new File(getFilesDir(), "theme_video.mp4");
                long size = 0;
                try (InputStream input = getContentResolver().openInputStream(uri);
                     FileOutputStream output = new FileOutputStream(source)) {
                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        size += read;
                        if (size > 200L * 1024 * 1024) throw new IllegalArgumentException("Video too large");
                        output.write(buffer, 0, read);
                    }
                }
                String command = "cp /data/user/0/dev.tiktokrootmod/files/theme_video.mp4 " +
                        VideoBackground.VIDEO_PATH + " && chmod 644 " + VideoBackground.VIDEO_PATH;
                Process process = new ProcessBuilder("su", "-c", command).start();
                success = process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0;
            } catch (Exception ignored) { }
            final boolean saved = success;
            runOnUiThread(() -> {
                if (!saved) {
                    Toast.makeText(this, "Không lưu được video (giới hạn 200 MB).",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                boolean restored = restoreOpacity("theme_video_opacity");
                boolean auto = restored ? prefs.getBoolean("auto_restart", false) : scheduleAutoRestart();
                Toast.makeText(this, "Đã lưu video" + (restored ? ", độ mờ đặt lại 40%" : "") +
                        (auto ? ". TikTok sẽ tự khởi động lại." : ". Mở lại TikTok để áp dụng."),
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private void clearThemeVideo() {
        setOpacity("theme_video_opacity", 0);
        syncSettings();
        SAVES.execute(() -> {
            try { new ProcessBuilder("su", "-c", "rm -f " + VideoBackground.VIDEO_PATH)
                    .start().waitFor(30, TimeUnit.SECONDS); }
            catch (Exception ignored) { }
        });
        Toast.makeText(this, "Đã xóa video nền. Mở lại TikTok để áp dụng.", Toast.LENGTH_LONG).show();
    }

    private void saveLauncherIcon(Uri uri) {
        SAVES.execute(() -> {
            boolean success = false;
            try {
                Bitmap original = decodeImage(uri, 512);
                Bitmap square = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(square);
                android.graphics.Paint paint = new android.graphics.Paint(3);
                int side = Math.min(original.getWidth(), original.getHeight());
                int left = (original.getWidth() - side) / 2;
                int top = (original.getHeight() - side) / 2;
                canvas.drawBitmap(original, new android.graphics.Rect(left, top, left + side, top + side),
                        new android.graphics.Rect(0, 0, 256, 256), paint);
                original.recycle();
                File source = new File(getFilesDir(), "icon.png");
                try (FileOutputStream output = new FileOutputStream(source)) {
                    if (!square.compress(Bitmap.CompressFormat.PNG, 100, output))
                        throw new IllegalStateException("PNG encode failed");
                }
                square.recycle();
                String command = "mkdir -p /data/user/0/com.miui.home/files && " +
                        "chmod 755 /data/user/0/com.miui.home/files && " +
                        "cp /data/user/0/dev.tiktokrootmod/files/icon.png " + IconHooks.ICON_PATH +
                        " && chmod 644 " + IconHooks.ICON_PATH + " && am force-stop com.miui.home";
                Process process = new ProcessBuilder("su", "-c", command).start();
                success = process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0;
            } catch (Exception ignored) { }
            final boolean saved = success;
            runOnUiThread(() -> Toast.makeText(this, saved ?
                    "Đã lưu icon. MIUI Home đang tải lại; bật scope MIUI Home trong LSPosed nếu chưa bật." :
                    "Không đổi được icon. Kiểm tra quyền root.", Toast.LENGTH_LONG).show());
        });
    }

    private void resetLauncherIcon() {
        SAVES.execute(() -> {
            boolean success = false;
            try {
                Process process = new ProcessBuilder("su", "-c", "rm -f " + IconHooks.ICON_PATH +
                        " && am force-stop com.miui.home").start();
                success = process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0;
            } catch (Exception ignored) { }
            final boolean reset = success;
            runOnUiThread(() -> Toast.makeText(this, reset ? "Đã về logo TikTok gốc." :
                    "Không khôi phục được icon.", Toast.LENGTH_LONG).show());
        });
    }

    private void saveFields() {
        try {
            long minLikes = parse(likes);
            long minViews = parse(views);
            long minTime = parse(publishTime);
            String iso = regionIso.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            String operator = regionOperator.getText().toString().trim();
            String name = regionName.getText().toString().trim();
            if (!iso.matches("[a-z]{2}") || !operator.matches("[0-9]{5,6}") || name.isEmpty()) {
                Toast.makeText(this, "Kiểm tra mã vùng và nhà mạng.", Toast.LENGTH_LONG).show();
                return;
            }
            prefs.edit().putLong("min_likes", minLikes).putLong("min_views", minViews)
                    .putLong("min_publish_time", minTime)
                    .putString("filter_words", filterWords.getText().toString().trim())
                    .putString("region_iso", iso).putString("region_operator", operator)
                    .putString("region_operator_name", name).apply();
            syncSettings();
            Toast.makeText(this, "Đang lưu qua KernelSU. Hãy mở lại TikTok sau khi lưu xong.", Toast.LENGTH_LONG).show();
        } catch (NumberFormatException error) {
            Toast.makeText(this, "Chỉ nhập số nguyên không âm.", Toast.LENGTH_LONG).show();
        }
    }

    private long parse(EditText input) {
        String value = input.getText().toString().trim();
        return value.isEmpty() ? 0 : Long.parseLong(value);
    }

    private void showRegionPicker() {
        ArrayList<String[]> regions = new ArrayList<>();
        try (BufferedReader input = new BufferedReader(new InputStreamReader(
                getAssets().open("regions.tsv"), "UTF-8"))) {
            String line;
            while ((line = input.readLine()) != null) {
                if (line.startsWith("#")) continue;
                String[] columns = line.split("\t", -1);
                if (columns.length == 4) regions.add(columns);
            }
        } catch (Exception error) {
            Toast.makeText(this, "Không đọc được danh sách vùng.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[regions.size()];
        for (int i = 0; i < names.length; i++) names[i] = regions.get(i)[0];
        dialog().setTitle("Chọn nước giả lập")
                .setItems(names, (dialog, which) -> {
                    String[] selected = regions.get(which);
                    regionIso.setText(selected[1]);
                    regionOperator.setText(selected[2]);
                    regionName.setText(selected[3]);
                    Toast.makeText(this, "Nhấn Lưu cài đặt để áp dụng.", Toast.LENGTH_SHORT).show();
                }).setNegativeButton("Hủy", null).show();
    }

    private String encodedSettings() throws Exception {
        JSONObject values = new JSONObject();
        for (String key : new String[]{"hide_ads", "hide_lives", "hide_photos", "hide_stories",
                "hide_series", "hide_paid", "clean_links", "spoof_region", "allow_screenshots",
                "profile_background", "always_show_seekbar", "minimal_ui", "anti_burnout",
                "remove_download_watermark", "unlimited_share_recipients", "unlimited_pinned_chats",
                "disable_live_auto_translate", "theme_rainbow"}) {
            boolean fallback = key.equals("hide_ads") || key.equals("clean_links") ||
                    key.equals("spoof_region") || key.equals("allow_screenshots") ||
                    key.equals("profile_background") || key.equals("disable_live_auto_translate");
            values.put(key, prefs.getBoolean(key, fallback));
        }
        values.put("region_iso", prefs.getString("region_iso", "kz"));
        values.put("font_family", prefs.getString("font_family", "default"));
        values.put("theme_color", prefs.getString("theme_color", "#FF2D55"));
        values.put("theme_color_opacity", prefs.getInt("theme_color_opacity", 0));
        values.put("theme_image_opacity", prefs.getInt("theme_image_opacity", 0));
        values.put("theme_video_opacity", prefs.getInt("theme_video_opacity", 0));
        values.put("region_operator", prefs.getString("region_operator", "40101"));
        values.put("region_operator_name", prefs.getString("region_operator_name", "Beeline"));
        values.put("filter_words", prefs.getString("filter_words", ""));
        values.put("min_likes", prefs.getLong("min_likes", 0));
        values.put("min_views", prefs.getLong("min_views", 0));
        values.put("min_publish_time", prefs.getLong("min_publish_time", 0));
        return Base64.encodeToString(values.toString().getBytes("UTF-8"), Base64.NO_WRAP);
    }

    private void syncSettings() {
        try {
            String encoded = encodedSettings();
            SAVES.execute(() -> {
                boolean saved = false;
                try {
                    Process process = new ProcessBuilder("su", "-c", "settings put global " +
                            Config.SYSTEM_KEY + " " + encoded).start();
                    if (process.waitFor(30, TimeUnit.SECONDS)) saved = process.exitValue() == 0;
                    else process.destroy();
                } catch (Exception ignored) { }
                final boolean success = saved;
                runOnUiThread(() -> {
                    boolean auto = success && prefs.getBoolean("auto_restart", false);
                    Toast.makeText(this,
                            success ? (auto ? "Đã lưu; TikTok sẽ tự khởi động lại sau 3 giây." :
                                    "Đã lưu; mở lại TikTok để áp dụng.") :
                                    "Chưa lưu được. Hãy cấp quyền root cho TikGoon trong KernelSU.",
                            Toast.LENGTH_LONG).show();
                    if (auto) {
                        handler.removeCallbacks(restartTask);
                        handler.postDelayed(restartTask, 3000);
                    }
                    if (success) rootState = 1;
                    renderAll();
                });
            });
        } catch (Exception error) {
            Toast.makeText(this, "Không thể chuẩn bị cài đặt.", Toast.LENGTH_LONG).show();
        }
    }
}
