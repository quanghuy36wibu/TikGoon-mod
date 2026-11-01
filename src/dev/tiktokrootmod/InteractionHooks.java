package dev.tiktokrootmod;

import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * "Luôn hiện ngày đăng" và "Ẩn phản ứng nhanh ở bình luận", tham khảo bản vá Morphe.
 *
 * Morphe sửa trực tiếp bytecode các lớp bị làm rối của TikTok 46.2.3 (X.0BIZ.LIZ, v.v.) nên không áp dụng
 * nguyên xi được cho bản 47.5.3. Ở đây dùng cách không phụ thuộc tên bị làm rối và ghi nhật ký chi tiết
 * (chỉ khi bật "Ghi log debug") để tinh chỉnh tiếp.
 */
final class InteractionHooks {
    private static final String AUTHOR_VM =
            "com.ss.android.ugc.aweme.feed.assem.videoauthorinfo.VideoAuthorInfoVM";

    private static final HashMap<Integer, Boolean> ID_CACHE = new HashMap<>();
    private static final HashMap<Class<?>, Boolean> CLASS_CACHE = new HashMap<>();
    private static final Set<String> SEEN = new HashSet<>();
    private static boolean authorInstalled;

    private InteractionHooks() {}

    // ───────────────────────── Không cần DexKit ─────────────────────────
    static void install(ClassLoader loader) {
        if (Config.HIDE_QUICK_REACTIONS) installQuickReactions();
        if (Config.ALWAYS_SHOW_PUBLISH_DATE) installPublishDate(loader);
    }

    /** Gọi khi DexKit đã sẵn sàng (từ FeedHooks.install) để thử lại phần cần tra cứu. */
    static void installWithDexKit(ClassLoader loader) {
        if (Config.ALWAYS_SHOW_PUBLISH_DATE) installPublishDate(loader);
    }

    // ───────────────────── Ẩn phản ứng nhanh ở bình luận ─────────────────────
    private static void installQuickReactions() {
        XposedBridge.hookAllMethods(View.class, "onAttachedToWindow", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                View view = (View) param.thisObject;
                if (isQuickReaction(view)) view.setVisibility(View.GONE);
            }
        });
        XposedBridge.hookAllMethods(View.class, "setVisibility", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                View view = (View) param.thisObject;
                Object arg = param.args[0];
                if (arg instanceof Integer && (Integer) arg != View.GONE && isQuickReaction(view))
                    param.args[0] = View.GONE;
            }
        });
        HookLog.log("TikTokRootMod: quick-reaction hide hooks installed");
    }

    private static boolean matches(String name) {
        String s = name.toLowerCase(Locale.ROOT);
        return s.contains("quickcomment") || s.contains("quick_comment") || s.contains("quickreaction")
                || s.contains("quick_reaction") || s.contains("quickemoji") || s.contains("quick_emoji")
                || s.contains("commentquick") || s.contains("comment_quick");
    }

    private static boolean isQuickReaction(View view) {
        try {
            Class<?> cls = view.getClass();
            Boolean byClass = CLASS_CACHE.get(cls);
            if (byClass == null) {
                byClass = matches(cls.getName());
                CLASS_CACHE.put(cls, byClass);
                if (byClass) HookLog.log("TikTokRootMod: quick-reaction view theo lớp: " + cls.getName());
            }
            if (byClass) return true;
            int id = view.getId();
            if (id == View.NO_ID || (id >>> 24) != 0x7f) return false;
            Boolean byId = ID_CACHE.get(id);
            if (byId == null) {
                String entry = view.getResources().getResourceEntryName(id);
                byId = matches(entry);
                ID_CACHE.put(id, byId);
                if (byId) HookLog.log("TikTokRootMod: quick-reaction view theo id: " + entry);
                else if (Config.LOG_ENABLED && SEEN.size() < 40 && entry.toLowerCase(Locale.ROOT).contains("quick")
                        && SEEN.add(entry)) HookLog.log("TikTokRootMod: view có 'quick' (chưa khớp): " + entry
                        + " [" + cls.getName() + "]");
            }
            return byId;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ───────────────────────── Luôn hiện ngày đăng ─────────────────────────
    private static synchronized void installPublishDate(ClassLoader loader) {
        if (authorInstalled) return;
        Class<?> vm = XposedHelpers.findClassIfExists(AUTHOR_VM, loader);
        if (vm == null) {
            HookLog.log("TikTokRootMod: không thấy " + AUTHOR_VM);
            return;
        }
        authorInstalled = true;
        StringBuilder all = new StringBuilder();
        for (Method m : vm.getDeclaredMethods()) {
            all.append(m.getName()).append('(');
            for (Class<?> t : m.getParameterTypes()) all.append(t.getSimpleName()).append(',');
            all.append(")->").append(m.getReturnType().getSimpleName()).append(' ');
        }
        HookLog.log("TikTokRootMod: VideoAuthorInfoVM methods: " + all);

        int hooked = 0;
        for (Method m : vm.getDeclaredMethods()) {
            if (!m.getName().contains("paramSync2StateAccept")) continue;
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    // Với "state" là kết quả trả về hoặc đối số: bật mọi cờ boolean có tên chứa time/date
                    forceTime(param.getResult());
                    for (Object arg : param.args) forceTime(arg);
                }
            });
            hooked++;
        }
        HookLog.log("TikTokRootMod: VideoAuthorInfoVM.paramSync2StateAccept hooks=" + hooked);
    }

    private static int timeLogs;

    private static void forceTime(Object state) {
        if (state == null) return;
        Class<?> cls = state.getClass();
        String n = cls.getName();
        if (n.startsWith("java.") || n.startsWith("android.") || n.contains("VideoItemParams")) return;
        try {
            for (Field f : cls.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                Class<?> type = f.getType();
                if (timeLogs < 12 && Config.LOG_ENABLED)
                    HookLog.log("TikTokRootMod: state " + cls.getSimpleName() + "." + f.getName() + ":" + type.getSimpleName());
                String name = f.getName().toLowerCase(Locale.ROOT);
                if ((type == boolean.class || type == Boolean.class)
                        && (name.contains("time") || name.contains("date"))) {
                    f.setAccessible(true);
                    f.set(state, Boolean.TRUE);
                    if (Config.LOG_ENABLED) HookLog.log("TikTokRootMod: bật " + cls.getSimpleName() + "." + f.getName());
                }
            }
            timeLogs++;
        } catch (Throwable error) {
            HookLog.log("TikTokRootMod: forceTime lỗi: " + error);
        }
    }
}
