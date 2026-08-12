package io.github.pigerzhu.onelab.diagnostics;

import io.github.pigerzhu.onelab.contract.SettingsKeys;

/** Stable feature metadata shared by the report generator. */
final class DiagnosticCatalog {
    static final Feature[] FEATURES = {
            feature("network.captive_portal", "Captive portal keep-alive",
                    SettingsKeys.KEY_ENABLE_CAPTIVE_KEEPER),
            feature("apps.gallery_labs", "Gallery developer Labs",
                    SettingsKeys.KEY_ENABLE_GALLERY_DEV_LABS),
            feature("apps.gallery_labs_zh_cn", "Gallery Labs Simplified Chinese",
                    SettingsKeys.KEY_ENABLE_GALLERY_LABS_ZH_CN),
            feature("apps.bilibili_fold", "Bilibili large-screen support",
                    SettingsKeys.KEY_ENABLE_BILI_FOLD_GATE, "tv.danmaku.bili"),
            feature("apps.bilibili_tablet_layout", "Bilibili tablet layout",
                    SettingsKeys.KEY_ENABLE_BILI_TABLET_LAYOUT, "tv.danmaku.bili"),
            feature("apps.baidu_large_screen", "Baidu full large-screen mode",
                    SettingsKeys.KEY_ENABLE_BAIDU_LARGE_SCREEN, "com.baidu.searchbox"),
            feature("apps.ctrip_split", "Ctrip split view",
                    SettingsKeys.KEY_ENABLE_CTRIP_SPLIT_RULES, "ctrip.android.view"),
            feature("apps.umetrip_split", "Umetrip split view",
                    SettingsKeys.KEY_ENABLE_UMETRIP_SPLIT_RULES,
                    "com.umetrip.android.msky.app"),
            feature("apps.meituan_split", "Meituan split view",
                    SettingsKeys.KEY_ENABLE_MEITUAN_SPLIT_RULES, "com.sankuai.meituan"),
            feature("apps.zhuanzhuan_split", "Zhuanzhuan split view",
                    SettingsKeys.KEY_ENABLE_ZHUANZHUAN_SPLIT_RULES,
                    "com.wuba.zhuanzhuan"),
            feature("apps.tongcheng_split", "Tongcheng Travel split view",
                    SettingsKeys.KEY_ENABLE_TONGCHENG_SPLIT_RULES,
                    "com.tongcheng.android"),
            feature("apps.xiaomi_shop_fold", "Xiaomi Store foldable support",
                    SettingsKeys.KEY_ENABLE_XIAOMI_SHOP_FOLD, "com.xiaomi.shop"),
            feature("apps.qq_fold", "QQ large-screen support",
                    SettingsKeys.KEY_ENABLE_QQ_FOLD_LAYOUT, "com.tencent.mobileqq"),
            feature("apps.ithome_embedding", "ITHome Activity Embedding",
                    SettingsKeys.KEY_ENABLE_ITHOME_ACTIVITY_EMBEDDING,
                    "com.ruanmei.ithome"),
            feature("apps.hupu_embedding", "Hupu Activity Embedding",
                    SettingsKeys.KEY_ENABLE_HUPU_ACTIVITY_EMBEDDING, "com.hupu.games"),
            feature("apps.xhs_home", "Xiaohongshu large-screen home layout",
                    SettingsKeys.KEY_ENABLE_XHS_FOLD_HOME, "com.xingin.xhs"),
            feature("apps.xhs_video", "Xiaohongshu new video post layout",
                    SettingsKeys.KEY_ENABLE_XHS_FOLD_VIDEO, "com.xingin.xhs"),
            feature("apps.instagram_two_pane_comments", "Instagram side comments",
                    SettingsKeys.KEY_ENABLE_INSTAGRAM_TWO_PANE_COMMENTS,
                    "com.instagram.android"),
            feature("apps.tiktok_side_comments", "TikTok side comments",
                    SettingsKeys.KEY_ENABLE_TIKTOK_SIDE_COMMENTS,
                    "com.zhiliaoapp.musically"),
            feature("apps.tiktok_live_multi_screen", "TikTok live drawer avoidance",
                    SettingsKeys.KEY_ENABLE_TIKTOK_LIVE_MULTI_SCREEN,
                    "com.zhiliaoapp.musically"),
            feature("apps.tiktok_portrait_large_screen", "TikTok portrait large-screen experiment",
                    SettingsKeys.KEY_ENABLE_TIKTOK_PORTRAIT_LARGE_SCREEN,
                    "com.zhiliaoapp.musically"),
            feature("apps.netease_half_fold_player", "NetEase Cloud Music half-fold player",
                    SettingsKeys.KEY_ENABLE_NETEASE_HALF_FOLD_PLAYER,
                    "com.netease.cloudmusic"),
            feature("thermal.master", "SDHMS thermal control",
                    SettingsKeys.KEY_ENABLE_SDHMS_THERMAL),
            feature("thermal.brightness", "Remove thermal brightness limit",
                    SettingsKeys.KEY_DISABLE_SDHMS_BRIGHTNESS_LIMIT),
            feature("thermal.modem", "Remove mobile network thermal limit",
                    SettingsKeys.KEY_DISABLE_SDHMS_CP_THERMAL_MITIGATION),
            feature("thermal.performance", "SIOP performance cap bypass",
                    SettingsKeys.KEY_ENABLE_SDHMS_PERF_CAP_BYPASS),
            feature("thermal.cpu", "CPU frequency cap release",
                    SettingsKeys.KEY_ENABLE_SDHMS_CPU_CAP_RELEASE),
            feature("thermal.multiwindow", "Remove thermal multi-window limit",
                    SettingsKeys.KEY_DISABLE_SSRM_MULTIWINDOW_LIMIT),
            feature("experiments.gpu_range", "GPU frequency range experiment",
                    SettingsKeys.KEY_ENABLE_GPU_RANGE_EXPERIMENT)
    };

    static final Value[] VALUES = {
            value("network.captive_delay_ms", SettingsKeys.KEY_CAPTIVE_DELAY_MS),
            value("experiments.gpu_range_min_mhz", SettingsKeys.KEY_GPU_RANGE_MIN_MHZ),
            value("experiments.gpu_range_max_mhz", SettingsKeys.KEY_GPU_RANGE_MAX_MHZ),
            value("experiments.gpu_range_runtime_status",
                    SettingsKeys.KEY_GPU_RANGE_RUNTIME_STATUS),
            value("experiments.gpu_supported_frequencies",
                    SettingsKeys.KEY_GPU_SUPPORTED_FREQUENCIES),
            value("display.aspect_ratio_overrides",
                    SettingsKeys.KEY_ASPECT_RATIO_OVERRIDES),
            value("display.refresh_rate_overrides",
                    SettingsKeys.KEY_REFRESH_RATE_OVERRIDES),
            value("display.split_view_ratio_overrides",
                    SettingsKeys.KEY_SPLIT_VIEW_RATIO_OVERRIDES)
    };

    private DiagnosticCatalog() {
    }

    private static Feature feature(String id, String label, String key) {
        return new Feature(id, label, key, null);
    }

    private static Feature feature(String id, String label, String key, String packageName) {
        return new Feature(id, label, key, packageName);
    }

    private static Value value(String id, String key) {
        return new Value(id, key);
    }

    static final class Feature {
        final String id;
        final String label;
        final String settingKey;
        final String packageName;

        Feature(String id, String label, String settingKey, String packageName) {
            this.id = id;
            this.label = label;
            this.settingKey = settingKey;
            this.packageName = packageName;
        }
    }

    static final class Value {
        final String id;
        final String settingKey;

        Value(String id, String settingKey) {
            this.id = id;
            this.settingKey = settingKey;
        }
    }
}
