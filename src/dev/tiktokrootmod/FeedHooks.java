package dev.tiktokrootmod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import dev.tiktokrootmod.dexkit.DexKitRuntime;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.result.ClassDataList;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class FeedHooks {
    private static final String FEED =
            "com.ss.android.ugc.aweme.feed.model.FeedItemList";
    private static final String AWEME =
            "com.ss.android.ugc.aweme.feed.model.Aweme";
    private static final String EXT =
            "com.ss.android.ugc.aweme.feed.model.AwemeExtKt";

    private static Class<?> awemeClass;
    private static Class<?> extClass;
    private static boolean loggedLiveFilter;

    private FeedHooks() {}

    static void install(ClassLoader loader) {
        HookLog.log("TikGoon: FeedHooks resolution started; DexKit runtime="
                + (Entry.getDexKitRuntime() == null ? "unavailable" : "available"));
        MediaHooks.installAcl(loader);   // DexKit đã sẵn sàng: thử lại nếu lần đầu chưa tìm thấy ACLCommonShare
        Class<?> feedClass = resolveClass(FEED, loader, "feed model");
        awemeClass = resolveClass(AWEME, loader, "Aweme model");
        extClass = resolveClass(EXT, loader, "Aweme extensions");

        if (feedClass == null || awemeClass == null) {
            HookLog.log("TikGoon: required feed class resolution failed; "
                    + "feed=" + classState(feedClass)
                    + ", aweme=" + classState(awemeClass)
                    + "; feed hooks skipped");
            return;
        }
        HookLog.log("TikGoon: required feed classes ready; feed="
                + feedClass.getName() + ", aweme=" + awemeClass.getName()
                + ", extensions=" + classState(extClass));

        hookListGetter(feedClass, "getAwemeList");
        hookListGetter(feedClass, "getItems");

        String[][] otherFeeds = {
            {
                "com.ss.android.ugc.aweme.feed.module.FollowingInterestFeedResponse",
                "getAwemeList"
            },
            {
                "com.ss.android.ugc.aweme.relation.model.MaFUserVideoListResponse",
                "getAwemeList"
            },
            {
                "com.ss.android.ugc.aweme.api.ArtistMusicAwemeResponse",
                "getAwemeList"
            },
            {
                "com.ss.android.ugc.aweme.friendstab.api.FriendsFeedResponse",
                "getAwemeList"
            },
            {
                "com.ss.android.ugc.aweme.footnote.detail.repo.FootNoteFeedItemList",
                "getItems"
            },
            {
                "com.ss.android.ugc.aweme.forward.model.ForwardItemList",
                "getItems"
            },
            {
                "com.ss.android.ugc.aweme.music.model.FanSpotlightPickedVideosResponse",
                "getAwemeList"
            }
        };

        for (String[] spec : otherFeeds) {
            Class<?> optional = resolveOptionalFeed(
                    spec[0], spec[1], loader);
            if (optional != null) {
                hookListGetter(optional, spec[1]);
            } else {
                HookLog.log("TikGoon: optional feed unavailable; skipped "
                        + spec[0] + "." + spec[1]);
            }
        }

        if (Config.HIDE_ADS) {
            hookEmptyListGetter(feedClass, "getPreloadAds");
            hookFalseGetter(feedClass, "isHasAd");
        }
    }

    /**
     * Resolve by DexKit first, then use the original class-name lookup as a
     * compatibility fallback. Logs explicitly identify the resolution source.
     */
    private static Class<?> resolveClass(
            String className,
            ClassLoader loader,
            String label) {
        DexKitRuntime runtime = Entry.getDexKitRuntime();
        if (runtime == null) {
            HookLog.log("TikGoon: DexKit unavailable for " + label
                    + "; attempting legacy lookup for " + className);
        } else {
            try {
                ClassDataList matches = runtime.getBridge().findClass(
                        FindClass.create().matcher(
                                new ClassMatcher().className(
                                        className, StringMatchType.Equals, false)));
                if (matches != null && !matches.isEmpty()) {
                    Class<?> resolved = matches.get(0).getInstance(loader);
                    if (resolved != null) {
                        HookLog.log("TikGoon: DexKit RESOLVED " + label
                                + " -> " + resolved.getName());
                        return resolved;
                    }
                    HookLog.log("TikGoon: DexKit returned a class match but "
                            + "could not load it for " + label
                            + "; attempting legacy lookup for " + className);
                } else {
                    HookLog.log("TikGoon: DexKit NO MATCH for " + label
                            + " (" + className + "); attempting legacy lookup");
                }
            } catch (Throwable error) {
                HookLog.log("TikGoon: DexKit ERROR for " + label
                        + " (" + className + "); attempting legacy lookup");
                HookLog.log(error);
            }
        }

        try {
            Class<?> fallback = XposedHelpers.findClassIfExists(className, loader);
            if (fallback != null) {
                HookLog.log("TikGoon: LEGACY FALLBACK RESOLVED " + label
                        + " -> " + fallback.getName());
            } else {
                HookLog.log("TikGoon: CLASS NOT FOUND " + label
                        + "; DexKit and legacy lookup both failed for " + className);
            }
            return fallback;
        } catch (Throwable error) {
            HookLog.log("TikGoon: LEGACY LOOKUP ERROR for " + label
                    + " (" + className + ")");
            HookLog.log(error);
            return null;
        }
    }

    /**
     * Resolve optional feed responses without relying exclusively on their
     * package/class names. The structural query requires a no-argument getter
     * returning java.util.List; candidates are then checked for an Aweme-like
     * generic element type before any hook is installed.
     */
    private static Class<?> resolveOptionalFeed(
            String className,
            String getterName,
            ClassLoader loader) {
        // Prefer exact class-name resolution. When it succeeds, do not run a
        // broad structural scan: many unrelated response/model classes expose
        // the same List getter and can tie on generic Aweme scoring. Treating
        // that diagnostic ambiguity as a resolution failure only creates noise.
        Class<?> named = resolveClass(
                className, loader, "optional feed " + className);
        if (named != null) {
            HookLog.log("TikGoon: OPTIONAL FEED RESOLVED BY NAME optional feed="
                    + className + " selected=" + named.getName()
                    + " structuralFallback=skipped reason=exact class lookup succeeded");
            return named;
        }

        DexKitRuntime runtime = Entry.getDexKitRuntime();
        if (runtime == null) {
            HookLog.log("TikGoon: STRUCTURAL SKIPPED optional feed=" + className
                    + " getter=" + getterName + " reason=DexKit runtime unavailable"
                    + "; namedResolution=" + classState(named));
            return named;
        }

        try {
            HookLog.log("TikGoon: STRUCTURAL SEARCH START optional feed=" + className
                    + " getter=" + getterName
                    + " matcher=method-name-exact,return-type-exact-java.util.List,param-count=0"
                    + " namedResolution=" + classState(named));
            ClassMatcher structure = new ClassMatcher().addMethod(
                    new MethodMatcher()
                            .name(getterName, StringMatchType.Equals, false)
                            .returnType("java.util.List", StringMatchType.Equals, false)
                            .paramCount(0));
            ClassDataList matches = runtime.getBridge().findClass(
                    FindClass.create().matcher(structure));
            int count = matches == null ? 0 : matches.size();
            HookLog.log("TikGoon: STRUCTURAL QUERY RESULT optional feed="
                    + className + " getter=" + getterName + " candidateCount=" + count);

            if (count == 0) {
                HookLog.log("TikGoon: STRUCTURAL NO MATCH optional feed=" + className
                        + " getter=" + getterName
                        + " reason=no method satisfied all matcher predicates; check exact"
                        + " method name, return type java.util.List, and zero parameters");
                if (named != null) {
                    logGetterDiagnostics(named, getterName, "named class");
                    HookLog.log("TikGoon: STRUCTURAL NOT REQUIRED FOR SELECTION optional feed="
                            + className + " reason=named/legacy lookup succeeded");
                }
                return named;
            }

            Class<?> best = null;
            int bestScore = 0;
            boolean tied = false;
            for (int i = 0; i < count; i++) {
                Class<?> candidate = null;
                try {
                    candidate = matches.get(i).getInstance(loader);
                } catch (Throwable error) {
                    HookLog.log("TikGoon: STRUCTURAL CANDIDATE LOAD FAILED optional feed="
                            + className + " index=" + i + " error=" + error);
                }
                if (candidate == null) {
                    HookLog.log("TikGoon: STRUCTURAL CANDIDATE REJECTED optional feed="
                            + className + " index=" + i + " reason=class instance unavailable");
                    continue;
                }

                logGetterDiagnostics(candidate, getterName, "structural candidate index=" + i);
                boolean listGetter = hasListGetter(candidate, getterName);
                int score = listGetter ? awemeContainerScore(candidate, getterName) : 0;
                HookLog.log("TikGoon: STRUCTURAL CANDIDATE SCORE optional feed="
                        + className + " candidate=" + candidate.getName()
                        + " listGetter=" + listGetter + " awemeContainerScore=" + score);
                if (!listGetter) {
                    HookLog.log("TikGoon: STRUCTURAL CANDIDATE REJECTED optional feed="
                            + className + " candidate=" + candidate.getName()
                            + " reason=reflection did not confirm public zero-arg List getter");
                    continue;
                }
                if (score > bestScore) {
                    best = candidate;
                    bestScore = score;
                    tied = false;
                } else if (score > 0 && score == bestScore
                        && best != null && !best.equals(candidate)) {
                    tied = true;
                }
            }

            if (best != null && bestScore > 0 && !tied) {
                HookLog.log("TikGoon: STRUCTURAL UNIQUE BEST optional feed=" + className
                        + " candidate=" + best.getName() + " score=" + bestScore);
                if (named != null) {
                    HookLog.log("TikGoon: STRUCTURAL DIAGNOSTIC ONLY optional feed="
                            + className + " selectedBy=named/legacy lookup selected="
                            + named.getName() + " structuralCandidate=" + best.getName());
                    return named;
                }
                HookLog.log("TikGoon: DexKit STRUCTURAL RESOLVED optional feed="
                        + className + " -> " + best.getName() + " (score=" + bestScore + ")");
                return best;
            }
            HookLog.log("TikGoon: STRUCTURAL REJECTED ALL/AMBIGUOUS optional feed="
                    + className + " bestCandidate=" + classState(best)
                    + " bestScore=" + bestScore + " tied=" + tied
                    + " reason=" + (tied ? "multiple candidates share best score"
                            : "no candidate had a positive Aweme-container score"));
        } catch (Throwable error) {
            HookLog.log("TikGoon: STRUCTURAL SEARCH ERROR optional feed=" + className
                    + " getter=" + getterName + " exception=" + error);
            HookLog.log(error);
        }
        return named;
    }

    /** Logs the exact reflected signature used to diagnose structural mismatches. */
    private static void logGetterDiagnostics(
            Class<?> type, String getterName, String source) {
        if (type == null) return;
        boolean foundName = false;
        try {
            for (Method method : type.getDeclaredMethods()) {
                if (!getterName.equals(method.getName())) continue;
                foundName = true;
                HookLog.log("TikGoon: GETTER DIAGNOSTIC source=" + source
                        + " class=" + type.getName()
                        + " method=" + method.getName()
                        + " return=" + method.getReturnType().getName()
                        + " genericReturn=" + method.getGenericReturnType().getTypeName()
                        + " params=" + method.getParameterTypes().length
                        + " public=" + java.lang.reflect.Modifier.isPublic(method.getModifiers()));
            }
            if (!foundName) {
                HookLog.log("TikGoon: GETTER DIAGNOSTIC source=" + source
                        + " class=" + type.getName() + " getter=" + getterName
                        + " result=not found in declared methods");
            }
        } catch (Throwable error) {
            HookLog.log("TikGoon: GETTER DIAGNOSTIC ERROR class=" + type.getName()
                    + " getter=" + getterName + " error=" + error);
        }
        try {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                String generic = field.getGenericType().getTypeName();
                if (containsAwemeType(generic) || field.getName().toLowerCase(Locale.ROOT).contains("aweme")) {
                    HookLog.log("TikGoon: FIELD DIAGNOSTIC class=" + type.getName()
                            + " field=" + field.getName()
                            + " type=" + field.getType().getName()
                            + " genericType=" + generic);
                }
            }
        } catch (Throwable error) {
            HookLog.log("TikGoon: FIELD DIAGNOSTIC ERROR class=" + type.getName()
                    + " error=" + error);
        }
    }

    private static boolean hasListGetter(Class<?> type, String getterName) {
        try {
            Method method = type.getMethod(getterName);
            return method.getParameterTypes().length == 0
                    && List.class.isAssignableFrom(method.getReturnType());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int awemeContainerScore(Class<?> type, String getterName) {
        boolean getterHasAweme = false;
        boolean fieldHasAweme = false;
        try {
            Method getter = type.getMethod(getterName);
            getterHasAweme = containsAwemeType(
                    getter.getGenericReturnType().getTypeName());
        } catch (Throwable ignored) {
            return 0;
        }

        try {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                if (containsAwemeType(field.getGenericType().getTypeName())) {
                    fieldHasAweme = true;
                    break;
                }
            }
        } catch (Throwable ignored) {
            // Generic metadata is optional.
        }

        String lower = type.getName().toLowerCase(Locale.ROOT);
        int nameHints = 0;
        for (String hint : new String[] {
                "feed", "response", "aweme", "video", "forward",
                "music", "friend", "relation", "footnote" }) {
            if (lower.contains(hint)) {
                nameHints++;
            }
        }

        // Generic List<Aweme> is the strongest signal. When generic metadata
        // is erased on the getter, an Aweme-typed field plus the expected
        // getter is accepted, but competing candidates are still rejected.
        if (getterHasAweme) {
            return 10 + nameHints;
        }
        if (fieldHasAweme) {
            return ("getAwemeList".equals(getterName) ? 8 : 6) + nameHints;
        }
        return 0;
    }

    private static boolean containsAwemeType(String typeName) {
        if (typeName == null) {
            return false;
        }
        String normalized = typeName.toLowerCase(Locale.ROOT);
        return normalized.contains("aweme")
                || (awemeClass != null
                    && normalized.contains(awemeClass.getName().toLowerCase(Locale.ROOT)));
    }

    private static String classState(Class<?> type) {
        return type == null ? "not found" : type.getName();
    }

    private static void hookListGetter(
            Class<?> type,
            String name) {

        hook(type, name, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(
                    MethodHookParam param) {

                Object value = param.getResult();

                if (value instanceof List) {
                    param.setResult(
                            filter((List<?>) value)
                    );
                }
            }
        });
    }

    private static void hookEmptyListGetter(
            Class<?> type,
            String name) {

        hook(type, name, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(
                    MethodHookParam param) {

                param.setResult(Collections.emptyList());
            }
        });
    }

    private static void hookFalseGetter(
            Class<?> type,
            String name) {

        hook(type, name, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(
                    MethodHookParam param) {

                param.setResult(false);
            }
        });
    }

    private static void hook(
            Class<?> type,
            String name,
            XC_MethodHook callback) {

        try {
            if (!XposedBridge
                    .hookAllMethods(type, name, callback)
                    .isEmpty()) {

                HookLog.log(
                        "TikTokRootMod: hooked "
                                + type.getSimpleName()
                                + "."
                                + name
                );
            }
        } catch (Throwable error) {
            HookLog.log(
                    "TikTokRootMod: skipped "
                            + name
                            + ": "
                            + error
            );
        }
    }

    private static int filterCalls, hiddenTotal;

    private static List<?> filter(List<?> input) {
        if (Config.REMOVE_DOWNLOAD_WATERMARK) {
            // Vá địa chỉ tải sạch ngay khi feed về (không phụ thuộc hàm get có bị inline hay không)
            for (Object item : input) {
                try { if (awemeClass != null && awemeClass.isInstance(item)) MediaHooks.patchAweme(item); }
                catch (Throwable ignored) { }
            }
        }
        List<?> result;
        try {
            result = filterInner(input);
        } catch (Throwable error) {
            HookLog.log("TikTokRootMod: feed filter error, giữ nguyên danh sách: " + error);
            return input;
        }
        // An toàn: nếu gần hết danh sách sẽ bị ẩn (vd. màn xem series/nội dung trả phí chỉ có mục trả phí),
        // TikTok thường truy cập theo chỉ số trên danh sách gốc và có thể crash -> giữ nguyên danh sách.
        if (input.size() > 1 && result.size() * 2 < input.size()) {
            HookLog.log("TikTokRootMod: feed filter bỏ qua (sẽ ẩn " + (input.size() - result.size())
                    + "/" + input.size() + " mục) để tránh crash");
            return input;
        }
        if (filterCalls++ < 25 || result.size() != input.size())
            HookLog.log("TikTokRootMod: feed filter called: in=" + input.size() + " out=" + result.size()
                    + " (đã ẩn " + hiddenTotal + " mục từ đầu phiên)");
        return result;
    }

    private static List<?> filterInner(List<?> input) {
        if (input.isEmpty()) {
            return input;
        }

        ArrayList<Object> output = null;

        for (int index = 0;
             index < input.size();
             index++) {

            Object item = input.get(index);

            if (!shouldHide(item)) {

                if (output != null) {
                    output.add(item);
                }

            } else if (output == null) {

                output = new ArrayList<>(input.size());

                output.addAll(
                        input.subList(0, index)
                );
            }
        }

        /*
         * Important:
         *
         * Do not replace a valid non-empty server
         * response with an empty list.
         *
         * Some TikTok feed screens interpret an
         * empty filtered response as a failed request
         * and display:
         *
         * "Đã xảy ra lỗi"
         */
        if (output != null && output.isEmpty()) {
            return input;
        }

        return output == null ? input : output;
    }

    private static final java.util.HashMap<String, Integer> REASONS = new java.util.HashMap<>();

    private static boolean shouldHide(Object item) {
        boolean hide = shouldHideInner(item);
        if (hide) {
            hiddenTotal++;
            String why = why(item);
            Integer n = REASONS.get(why);
            n = n == null ? 1 : n + 1;
            REASONS.put(why, n);
            if (n <= 3) HookLog.log("TikTokRootMod: feed item hidden, reason=" + why + " (lần " + n + ")");
        }
        return hide;
    }

    /** Chẩn đoán: cho biết điều kiện nào làm mục bị ẩn (chỉ gọi khi mục đã bị ẩn). */
    private static String why(Object item) {
        try {
            if (Config.HIDE_ADS && isAd(item)) return "ad";
            if (Config.HIDE_LIVES && isLive(item)) return "live";
            if (Config.HIDE_PHOTOS && isPhotoPost(item)) return "photo";
            if (Config.HIDE_SHOP && isShop(item)) return "shop";
            if (Config.HIDE_STORIES) {
                if (Boolean.TRUE.equals(call(item, "getIsTikTokStory"))) return "story:getIsTikTokStory=true";
                Object v = call(item, "getStory");
                if (v != null) return "story:getStory=" + v.getClass().getSimpleName();
                v = call(item, "getUserStory");
                if (v != null) return "story:getUserStory=" + v.getClass().getSimpleName();
            }
            if (Config.HIDE_SERIES) {
                Object v = call(item, "getMixInfo");
                if (v != null) return "series:getMixInfo=" + v.getClass().getSimpleName();
            }
            if (Config.HIDE_PAID) {
                if (isPaid(item)) return "paid";
            }
            return "other(filter-words/min-likes/views/time)";
        } catch (Throwable error) {
            return "unknown:" + error;
        }
    }

    private static boolean shouldHideInner(Object item) {

        if (item == null ||
                !awemeClass.isInstance(item)) {
            return false;
        }

        if (Config.HIDE_ADS && isAd(item)) {
            return true;
        }

        if (Config.HIDE_LIVES && isLive(item)) {

            if (!loggedLiveFilter) {
                loggedLiveFilter = true;

                HookLog.log(
                        "TikTokRootMod: filtered a LIVE feed item"
                );
            }

            return true;
        }

        if (Config.HIDE_PHOTOS && isPhotoPost(item)) {
            return true;
        }

        if (Config.HIDE_SHOP && isShop(item)) {
            return true;
        }

        if (Config.HIDE_STORIES &&
                (
                    Boolean.TRUE.equals(
                            call(item, "getIsTikTokStory")
                    )
                    ||
                    call(item, "getStory") != null
                    ||
                    call(item, "getUserStory") != null
                )) {
            return true;
        }

        if (Config.HIDE_SERIES &&
                call(item, "getMixInfo") != null) {
            return true;
        }

        if (Config.HIDE_PAID && isPaid(item)) {
            return true;
        }

        if (!Config.FILTER_WORDS.isEmpty()) {

            Object description =
                    call(item, "getDesc");

            if (description instanceof String) {

                String lower =
                        ((String) description)
                                .toLowerCase(Locale.ROOT);

                for (String word :
                        Config.FILTER_WORDS.split(",")) {

                    String needle =
                            word.trim()
                                    .toLowerCase(Locale.ROOT);

                    if (!needle.isEmpty() &&
                            lower.contains(needle)) {
                        return true;
                    }
                }
            }
        }

        Object published =
                call(item, "getCreateTime");

        if (Config.MIN_PUBLISH_TIME_SECONDS > 0 &&
                published instanceof Number &&
                longValue(published) > 0 &&
                longValue(published)
                        < Config.MIN_PUBLISH_TIME_SECONDS) {

            return true;
        }

        Object stats =
                call(item, "getStatistics");

        Object likes =
                call(stats, "getDiggCount");

        Object views =
                call(stats, "getPlayCount");

        if (Config.MIN_LIKES > 0 &&
                likes instanceof Number &&
                longValue(likes) < Config.MIN_LIKES) {

            return true;
        }

        if (Config.MAX_LIKES > 0 &&
                likes instanceof Number &&
                longValue(likes) > Config.MAX_LIKES) {

            return true;
        }

        if (Config.MAX_VIEWS > 0 &&
                views instanceof Number &&
                longValue(views) > Config.MAX_VIEWS) {

            return true;
        }

        return Config.MIN_VIEWS > 0 &&
                views instanceof Number &&
                longValue(views) < Config.MIN_VIEWS;
    }

    /** Bài ảnh (ImageVideoFilter của Morphe): có danh sách ảnh, hoặc thông tin ảnh/chữ kiểu photo mode. */
    private static boolean isPhotoPost(Object item) {
        Object images = call(item, "getImageInfos");
        if (images instanceof java.util.Collection && !((java.util.Collection<?>) images).isEmpty()) {
            return true;
        }
        return call(item, "getPhotoModeImageInfo") != null
                || call(item, "getPhotoModeTextInfo") != null;
    }

    /** Video bán hàng / TikTok Shop (ShopFilter của Morphe): đường dẫn chia sẻ chứa placeholder_product_id. */
    private static boolean isShop(Object item) {
        Object url = call(item, "getShareUrl");
        return url instanceof String && ((String) url).contains("placeholder_product_id");
    }

    private static int paidDumps;

    /**
     * getMPaidContentInfo trên TikTok 47.5.3 trả về một đối tượng PaidContentInfo cho MỌI video
     * (kể cả không trả phí), nên chỉ kiểm tra "khác null" sẽ ẩn hết feed. Giờ chỉ coi là trả phí
     * khi isPaidContent=true hoặc đối tượng này có trường mang giá trị thật (true, số khác 0,
     * chuỗi/danh sách không rỗng).
     */
    private static boolean isPaid(Object item) {
        if (Boolean.TRUE.equals(call(item, "isPaidContent"))) return true;
        Object info = call(item, "getMPaidContentInfo");
        if (info == null) return false;
        boolean meaningful = false;
        StringBuilder dump = Config.LOG_ENABLED && paidDumps < 3 ? new StringBuilder() : null;
        try {
            for (java.lang.reflect.Field field : info.getClass().getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object value = field.get(info);
                if (dump != null) dump.append(field.getName()).append('=').append(value).append(' ');
                if (value instanceof Boolean) meaningful |= (Boolean) value;
                else if (value instanceof Number) meaningful |= ((Number) value).doubleValue() > 0;
                else if (value instanceof CharSequence) meaningful |= ((CharSequence) value).length() > 0;
                else if (value instanceof java.util.Collection) meaningful |= !((java.util.Collection<?>) value).isEmpty();
            }
        } catch (Throwable ignored) { }
        if (dump != null) {
            paidDumps++;
            HookLog.log("TikTokRootMod: PaidContentInfo " + info.getClass().getSimpleName() + " -> "
                    + dump + "=> paid=" + meaningful);
        }
        return meaningful;
    }

    private static boolean isAd(Object item) {

        if (extClass == null) {
            return false;
        }

        if (callExt(item, "getMonetizationData") != null) {
            return true;
        }

        // Bộ lọc quảng cáo của Morphe (AdsFilter): isAd / isSoftAd / nhạc quảng bá / dữ liệu quảng cáo thô
        for (String method : new String[] {"isAd", "isSoftAd", "isWithPromotionalMusic"}) {
            if (Boolean.TRUE.equals(call(item, method))) {
                return true;
            }
        }
        if (call(item, "getAwemeRawAd") != null) {
            return true;
        }

        for (String method :
                new String[] {
                    "isAdFiled",
                    "isAdTraffic",
                    "isMonetizationTraffic",
                    "isPseudoAd",
                    "isSearchPreciseAd"
                }) {

            if (Boolean.TRUE.equals(
                    callExt(item, method))) {
                return true;
            }
        }

        return false;
    }

    private static boolean isLive(Object item) {

        long type =
                longValue(
                        call(item, "getAwemeType")
                );

        if (type == 104 ||
                type == 105 ||
                longValue(
                        call(item, "getLiveId")
                ) != 0) {

            return true;
        }

        if (field(item, "newLiveRoomData") != null ||
                call(item, "getRoomFeedCellStruct") != null ||
                call(item, "getLiveExtraInfoStruct") != null) {

            return true;
        }

        return Boolean.TRUE.equals(
                call(item, "getAuthorLive")
        ) ||
        Boolean.TRUE.equals(
                call(item, "isLiveReplay")
        );
    }

    private static Object field(
            Object item,
            String name) {

        try {
            java.lang.reflect.Field field =
                    item.getClass()
                            .getDeclaredField(name);

            field.setAccessible(true);

            return field.get(item);

        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object callExt(
            Object item,
            String name) {

        try {
            Method method =
                    extClass.getDeclaredMethod(
                            name,
                            awemeClass
                    );

            method.setAccessible(true);

            return method.invoke(
                    null,
                    item
            );

        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object call(
            Object item,
            String name) {

        if (item == null) {
            return null;
        }

        try {
            Method method =
                    item.getClass()
                            .getMethod(name);

            return method.invoke(item);

        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long longValue(Object value) {

        return value instanceof Number
                ? ((Number) value).longValue()
                : 0;
    }
}
