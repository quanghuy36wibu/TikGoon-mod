package dev.tiktokrootmod;

import android.content.Context;
import android.provider.Settings;
import android.util.Base64;

import org.json.JSONObject;

import de.robv.android.xposed.XposedBridge;

public final class Config {
    static final String PREFS = "settings";
    static final String SYSTEM_KEY = "tiktokrootmod_settings";
    private Config() {}

    public static boolean HIDE_ADS = true;
    public static boolean HIDE_LIVES = false;
    public static boolean HIDE_PHOTOS = false;
    public static boolean HIDE_STORIES = false;
    public static boolean HIDE_SERIES = false;
    public static boolean HIDE_PAID = false;
    public static boolean CLEAN_SHARE_LINKS = true;
    public static boolean SPOOF_REGION = true;
    public static boolean ALLOW_SCREENSHOTS = true;
    public static boolean ENABLE_PROFILE_BACKGROUND = true;
    public static boolean ALWAYS_SHOW_SEEKBAR = false;
    public static boolean MINIMAL_UI = false;
    public static boolean ANTI_BURNOUT = false;
    public static boolean REMOVE_DOWNLOAD_WATERMARK = false;
    public static boolean UNLIMITED_SHARE_RECIPIENTS = false;
    public static boolean UNLIMITED_PINNED_CHATS = false;
    public static boolean DISABLE_LIVE_AUTO_TRANSLATE = true;
    public static boolean THEME_RAINBOW = false;
    public static boolean THEME_LIGHT = false;
    public static boolean COMMENT_SCRIM = false;
    public static boolean DISABLE_DOUBLE_TAP_LIKE = false;
    public static int COMMENT_SCRIM_OPACITY = 35;
    public static String THEME_COLOR = "#FF2D55";
    public static int THEME_COLOR_OPACITY = 0;
    public static int THEME_IMAGE_OPACITY = 0;
    public static int THEME_VIDEO_OPACITY = 0;
    public static String FONT_FAMILY = "default";
    public static String REGION_ISO = "kz";
    public static String REGION_OPERATOR = "40101";
    public static String REGION_OPERATOR_NAME = "Beeline";
    public static long MIN_LIKES = 0;
    public static long MIN_VIEWS = 0;
    public static long MIN_PUBLISH_TIME_SECONDS = 0;
    public static String FILTER_WORDS = "";

    static void load(Context context) {
        try {
            String encoded = Settings.Global.getString(context.getContentResolver(), SYSTEM_KEY);
            if (encoded == null || encoded.isEmpty()) {
                XposedBridge.log("TikTokRootMod: no saved settings; using defaults");
                return;
            }
            JSONObject values = new JSONObject(new String(Base64.decode(encoded, Base64.DEFAULT), "UTF-8"));
            HIDE_ADS = values.optBoolean("hide_ads", HIDE_ADS);
            HIDE_LIVES = values.optBoolean("hide_lives", HIDE_LIVES);
            HIDE_PHOTOS = values.optBoolean("hide_photos", HIDE_PHOTOS);
            HIDE_STORIES = values.optBoolean("hide_stories", HIDE_STORIES);
            HIDE_SERIES = values.optBoolean("hide_series", HIDE_SERIES);
            HIDE_PAID = values.optBoolean("hide_paid", HIDE_PAID);
            CLEAN_SHARE_LINKS = values.optBoolean("clean_links", CLEAN_SHARE_LINKS);
            SPOOF_REGION = values.optBoolean("spoof_region", SPOOF_REGION);
            ALLOW_SCREENSHOTS = values.optBoolean("allow_screenshots", ALLOW_SCREENSHOTS);
            ENABLE_PROFILE_BACKGROUND = values.optBoolean("profile_background", ENABLE_PROFILE_BACKGROUND);
            ALWAYS_SHOW_SEEKBAR = values.optBoolean("always_show_seekbar", ALWAYS_SHOW_SEEKBAR);
            MINIMAL_UI = values.optBoolean("minimal_ui", MINIMAL_UI);
            ANTI_BURNOUT = values.optBoolean("anti_burnout", ANTI_BURNOUT);
            REMOVE_DOWNLOAD_WATERMARK = values.optBoolean("remove_download_watermark", REMOVE_DOWNLOAD_WATERMARK);
            UNLIMITED_SHARE_RECIPIENTS = values.optBoolean("unlimited_share_recipients", UNLIMITED_SHARE_RECIPIENTS);
            UNLIMITED_PINNED_CHATS = values.optBoolean("unlimited_pinned_chats", UNLIMITED_PINNED_CHATS);
            DISABLE_LIVE_AUTO_TRANSLATE = values.optBoolean("disable_live_auto_translate", DISABLE_LIVE_AUTO_TRANSLATE);
            THEME_RAINBOW = values.optBoolean("theme_rainbow", THEME_RAINBOW);
            THEME_LIGHT = values.optBoolean("theme_light", THEME_LIGHT);
            COMMENT_SCRIM = values.optBoolean("comment_scrim", COMMENT_SCRIM);
            DISABLE_DOUBLE_TAP_LIKE = values.optBoolean("disable_double_tap_like", DISABLE_DOUBLE_TAP_LIKE);
            COMMENT_SCRIM_OPACITY = Math.max(0, Math.min(80, values.optInt("comment_scrim_opacity", COMMENT_SCRIM_OPACITY)));
            THEME_COLOR = values.optString("theme_color", THEME_COLOR);
            THEME_COLOR_OPACITY = Math.max(0, Math.min(80, values.optInt("theme_color_opacity", THEME_COLOR_OPACITY)));
            THEME_IMAGE_OPACITY = Math.max(0, Math.min(80, values.optInt("theme_image_opacity", THEME_IMAGE_OPACITY)));
            THEME_VIDEO_OPACITY = Math.max(0, Math.min(80, values.optInt("theme_video_opacity", THEME_VIDEO_OPACITY)));
            FONT_FAMILY = values.optString("font_family", FONT_FAMILY);
            REGION_ISO = values.optString("region_iso", REGION_ISO);
            REGION_OPERATOR = values.optString("region_operator", REGION_OPERATOR);
            REGION_OPERATOR_NAME = values.optString("region_operator_name", REGION_OPERATOR_NAME);
            MIN_LIKES = Math.max(0, values.optLong("min_likes", MIN_LIKES));
            MIN_VIEWS = Math.max(0, values.optLong("min_views", MIN_VIEWS));
            MIN_PUBLISH_TIME_SECONDS = Math.max(0, values.optLong("min_publish_time", MIN_PUBLISH_TIME_SECONDS));
            FILTER_WORDS = values.optString("filter_words", FILTER_WORDS);
            XposedBridge.log("TikTokRootMod: settings loaded via Android Global Settings");
        } catch (Throwable error) {
            XposedBridge.log("TikTokRootMod: settings unavailable; using defaults: " + error);
        }
    }
}
