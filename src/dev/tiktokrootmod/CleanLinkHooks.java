package dev.tiktokrootmod;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.net.Uri;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

final class CleanLinkHooks {
    private static final Pattern URL = Pattern.compile("https?://[^\\s\\\"'<>]+", Pattern.CASE_INSENSITIVE);
    private static final Set<String> DROP = new HashSet<>(Arrays.asList(
            "_r", "_t", "share_item_id", "share_link_id", "share_app_id", "timestamp",
            "tt_from", "u_code", "ug_btm", "user_id", "source", "utm_source",
            "utm_medium", "utm_campaign", "enter_from", "enter_method", "iid",
            "device_id", "region", "sec_user_id", "did", "aid", "web_id",
            "webid", "mstoken", "share_scene", "share_uid", "share_author_id"));

    private CleanLinkHooks() {}

    static void install() {
        XposedBridge.hookAllMethods(ClipboardManager.class, "setPrimaryClip", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length == 0 || !(param.args[0] instanceof ClipData)) return;
                ClipData oldClip = (ClipData) param.args[0];
                if (oldClip.getItemCount() == 0) return;
                String[] texts = new String[oldClip.getItemCount()];
                boolean changed = false;
                for (int i = 0; i < texts.length; i++) {
                    CharSequence text = oldClip.getItemAt(i).getText();
                    if (text == null) return;
                    texts[i] = cleanText(text.toString());
                    changed |= !texts[i].contentEquals(text);
                }
                if (!changed) return;
                CharSequence label = oldClip.getDescription().getLabel();
                ClipData replacement = ClipData.newPlainText(label, texts[0]);
                for (int i = 1; i < texts.length; i++) replacement.addItem(new ClipData.Item(texts[i]));
                param.args[0] = replacement;
            }
        });
    }

    private static String cleanText(String text) {
        Matcher matcher = URL.matcher(text);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(output, Matcher.quoteReplacement(cleanUrl(matcher.group())));
        matcher.appendTail(output);
        return output.toString();
    }

    private static String cleanUrl(String input) {
        try {
            Uri uri = Uri.parse(input);
            String host = uri.getHost();
            if (host == null || !trustedHost(host.toLowerCase(java.util.Locale.ROOT))) return input;
            String query = uri.getEncodedQuery();
            if (query == null || query.isEmpty()) return input;
            StringBuilder kept = new StringBuilder();
            boolean changed = false;
            for (String part : query.split("&", -1)) {
                String encodedName = part.split("=", 2)[0];
                String name = Uri.decode(encodedName).toLowerCase(java.util.Locale.ROOT);
                if (DROP.contains(name)) { changed = true; continue; }
                if (kept.length() > 0) kept.append('&');
                kept.append(part);
            }
            if (!changed) return input;
            return uri.buildUpon().encodedQuery(kept.length() == 0 ? null : kept.toString()).build().toString();
        } catch (Throwable ignored) {
            return input;
        }
    }

    private static boolean trustedHost(String host) {
        for (String domain : new String[]{"tiktok.com", "tiktokv.com", "douyin.com", "musical.ly"})
            if (host.equals(domain) || host.endsWith("." + domain)) return true;
        return false;
    }
}
