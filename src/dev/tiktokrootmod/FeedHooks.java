package dev.tiktokrootmod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
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
        Class<?> feedClass =
                XposedHelpers.findClassIfExists(FEED, loader);
        awemeClass =
                XposedHelpers.findClassIfExists(AWEME, loader);
        extClass =
                XposedHelpers.findClassIfExists(EXT, loader);

        if (feedClass == null || awemeClass == null) {
            XposedBridge.log(
                    "TikTokRootMod: feed model not found; skipping");
            return;
        }

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
            Class<?> optional =
                    XposedHelpers.findClassIfExists(spec[0], loader);

            if (optional != null) {
                hookListGetter(optional, spec[1]);
            }
        }

        if (Config.HIDE_ADS) {
            hookEmptyListGetter(feedClass, "getPreloadAds");
            hookFalseGetter(feedClass, "isHasAd");
        }
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

                XposedBridge.log(
                        "TikTokRootMod: hooked "
                                + type.getSimpleName()
                                + "."
                                + name
                );
            }
        } catch (Throwable error) {
            XposedBridge.log(
                    "TikTokRootMod: skipped "
                            + name
                            + ": "
                            + error
            );
        }
    }

    private static List<?> filter(List<?> input) {
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

    private static boolean shouldHide(Object item) {

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

                XposedBridge.log(
                        "TikTokRootMod: filtered a LIVE feed item"
                );
            }

            return true;
        }

        if (Config.HIDE_PHOTOS &&
                call(item, "getPhotoModeImageInfo") != null) {
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

        if (Config.HIDE_PAID &&
                (
                    Boolean.TRUE.equals(
                            call(item, "isPaidContent")
                    )
                    ||
                    call(item, "getMPaidContentInfo") != null
                )) {
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

        return Config.MIN_VIEWS > 0 &&
                views instanceof Number &&
                longValue(views) < Config.MIN_VIEWS;
    }

    private static boolean isAd(Object item) {

        if (extClass == null) {
            return false;
        }

        if (callExt(item, "getMonetizationData") != null) {
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
