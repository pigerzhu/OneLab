package io.github.pigerzhu.onelab.feature.applications;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_COOLAPK_IMAGE_FULLSCREEN;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_WEIBO_IMAGE_FULLSCREEN;

import java.util.Map;

final class SplitImageFullscreenApps {
    private static final Map<String, String> SETTINGS_BY_PACKAGE = Map.of(
            "com.coolapk.market", KEY_ENABLE_COOLAPK_IMAGE_FULLSCREEN,
            "com.sina.weibo", KEY_ENABLE_WEIBO_IMAGE_FULLSCREEN);

    private SplitImageFullscreenApps() {
    }

    static boolean supports(String packageName) {
        return SETTINGS_BY_PACKAGE.containsKey(packageName);
    }

    static String settingKey(String packageName) {
        return SETTINGS_BY_PACKAGE.get(packageName);
    }
}
