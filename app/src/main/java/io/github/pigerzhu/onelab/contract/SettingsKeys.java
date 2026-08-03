package io.github.pigerzhu.onelab.contract;

/** Settings contract shared by the configuration UI and LSPosed runtime. */
public final class SettingsKeys {
    public static final String KEY_ENABLE_CAPTIVE_KEEPER = "onelab_captive_portal_keeper";
    public static final String KEY_CAPTIVE_DELAY_MS = "onelab_captive_portal_delay_ms";
    public static final String KEY_ENABLE_GALLERY_DEV_LABS = "onelab_gallery_dev_labs";
    public static final String KEY_ENABLE_BILI_FOLD_GATE = "onelab_bili_fold_gate";
    public static final String KEY_ENABLE_BILI_TABLET_LAYOUT = "onelab_bili_tablet_layout";
    public static final String KEY_ENABLE_BAIDU_LARGE_SCREEN =
            "onelab_baidu_large_screen";
    public static final String KEY_ENABLE_CTRIP_SPLIT_RULES = "onelab_ctrip_split_rules";
    public static final String KEY_ENABLE_UMETRIP_SPLIT_RULES =
            "onelab_umetrip_split_rules";
    public static final String KEY_ENABLE_MEITUAN_SPLIT_RULES =
            "onelab_meituan_split_rules";
    public static final String KEY_ENABLE_ZHUANZHUAN_SPLIT_RULES =
            "onelab_zhuanzhuan_split_rules";
    public static final String KEY_ENABLE_TONGCHENG_SPLIT_RULES =
            "onelab_tongcheng_split_rules";
    public static final String KEY_ENABLE_XIAOMI_SHOP_FOLD =
            "onelab_xiaomi_shop_fold";
    public static final String KEY_ENABLE_QQ_FOLD_LAYOUT = "onelab_qq_fold_layout";
    public static final String KEY_ENABLE_XHS_FOLD_HOME = "onelab_xhs_fold_home";
    public static final String KEY_ENABLE_XHS_FOLD_VIDEO = "onelab_xhs_fold_video";
    public static final String KEY_ENABLE_SDHMS_THERMAL = "onelab_sdhms_thermal_controls";
    public static final String KEY_DISABLE_SDHMS_BRIGHTNESS_LIMIT =
            "onelab_disable_sdhms_brightness_limit";
    public static final String KEY_DISABLE_SDHMS_CP_THERMAL_MITIGATION =
            "onelab_disable_sdhms_cp_thermal_mitigation";
    public static final String KEY_ENABLE_SDHMS_PERF_CAP_BYPASS =
            "onelab_sdhms_perf_cap_bypass";
    public static final String KEY_SDHMS_GPU_MIN_CAP_MHZ =
            "onelab_sdhms_gpu_min_cap_mhz";
    public static final String KEY_ENABLE_SDHMS_CPU_CAP_RELEASE =
            "onelab_sdhms_cpu_cap_release";
    public static final String KEY_DISABLE_SSRM_MULTIWINDOW_LIMIT =
            "onelab_disable_ssrm_multiwindow_limit";
    public static final String KEY_ASPECT_RATIO_OVERRIDES = "onelab_aspect_ratio_overrides";
    public static final String KEY_REFRESH_RATE_OVERRIDES = "onelab_refresh_rate_overrides";
    public static final String KEY_SPLIT_VIEW_RATIO_OVERRIDES =
            "onelab_split_view_ratio_overrides";
    public static final String KEY_SPLIT_VIEW_ALLOWED_PACKAGES =
            "onelab_split_view_allowed_packages";

    public static final long DEFAULT_CAPTIVE_DELAY_MS = 60_000L;
    public static final int DEFAULT_SDHMS_GPU_MIN_CAP_MHZ = 770;
    public static final int[] SDHMS_GPU_FREQS_MHZ = {
            80, 155, 231, 310, 366, 422, 500, 578,
            629, 680, 720, 770, 834, 903, 950, 1000
    };

    private SettingsKeys() {
    }
}
