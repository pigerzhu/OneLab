package io.github.pigerzhu.onelab.diagnostics;

import io.github.pigerzhu.onelab.contract.SettingsKeys;

/** Stable feature metadata shared by the report generator. */
final class DiagnosticCatalog {
    static final Feature[] FEATURES = {
            feature("network.captive_portal", "开放网络认证页保活",
                    SettingsKeys.KEY_ENABLE_CAPTIVE_KEEPER),
            feature("apps.gallery_labs", "图库开发者 Labs",
                    SettingsKeys.KEY_ENABLE_GALLERY_DEV_LABS),
            feature("apps.bilibili_fold", "哔哩哔哩大屏适配",
                    SettingsKeys.KEY_ENABLE_BILI_FOLD_GATE, "tv.danmaku.bili"),
            feature("apps.baidu_large_screen", "百度完整大屏模式",
                    SettingsKeys.KEY_ENABLE_BAIDU_LARGE_SCREEN, "com.baidu.searchbox"),
            feature("apps.ctrip_split", "携程旅行分屏视图",
                    SettingsKeys.KEY_ENABLE_CTRIP_SPLIT_RULES, "ctrip.android.view"),
            feature("apps.umetrip_split", "航旅纵横分屏视图",
                    SettingsKeys.KEY_ENABLE_UMETRIP_SPLIT_RULES,
                    "com.umetrip.android.msky.app"),
            feature("apps.meituan_split", "美团分屏视图",
                    SettingsKeys.KEY_ENABLE_MEITUAN_SPLIT_RULES, "com.sankuai.meituan"),
            feature("apps.zhuanzhuan_split", "转转分屏视图",
                    SettingsKeys.KEY_ENABLE_ZHUANZHUAN_SPLIT_RULES,
                    "com.wuba.zhuanzhuan"),
            feature("apps.tongcheng_split", "同程旅行分屏视图",
                    SettingsKeys.KEY_ENABLE_TONGCHENG_SPLIT_RULES,
                    "com.tongcheng.android"),
            feature("apps.xiaomi_shop_fold", "小米商城折叠屏适配",
                    SettingsKeys.KEY_ENABLE_XIAOMI_SHOP_FOLD, "com.xiaomi.shop"),
            feature("apps.qq_fold", "QQ 大屏适配",
                    SettingsKeys.KEY_ENABLE_QQ_FOLD_LAYOUT, "com.tencent.mobileqq"),
            feature("apps.xhs_home", "小红书首页大屏布局",
                    SettingsKeys.KEY_ENABLE_XHS_FOLD_HOME, "com.xingin.xhs"),
            feature("apps.xhs_video", "小红书新版视频贴布局",
                    SettingsKeys.KEY_ENABLE_XHS_FOLD_VIDEO, "com.xingin.xhs"),
            feature("thermal.master", "SDHMS 温控控制",
                    SettingsKeys.KEY_ENABLE_SDHMS_THERMAL),
            feature("thermal.brightness", "解除高温亮度限制",
                    SettingsKeys.KEY_DISABLE_SDHMS_BRIGHTNESS_LIMIT),
            feature("thermal.modem", "解除移动网络温控限制",
                    SettingsKeys.KEY_DISABLE_SDHMS_CP_THERMAL_MITIGATION),
            feature("thermal.performance", "SIOP 性能限频拦截",
                    SettingsKeys.KEY_ENABLE_SDHMS_PERF_CAP_BYPASS),
            feature("thermal.cpu", "CPU 限频释放",
                    SettingsKeys.KEY_ENABLE_SDHMS_CPU_CAP_RELEASE),
            feature("thermal.multiwindow", "解除高温多窗口限制",
                    SettingsKeys.KEY_DISABLE_SSRM_MULTIWINDOW_LIMIT)
    };

    static final Value[] VALUES = {
            value("network.captive_delay_ms", SettingsKeys.KEY_CAPTIVE_DELAY_MS),
            value("thermal.gpu_min_cap_mhz", SettingsKeys.KEY_SDHMS_GPU_MIN_CAP_MHZ),
            value("display.aspect_ratio_overrides",
                    SettingsKeys.KEY_ASPECT_RATIO_OVERRIDES),
            value("display.refresh_rate_overrides",
                    SettingsKeys.KEY_REFRESH_RATE_OVERRIDES)
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
