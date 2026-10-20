package io.github.pigerzhu.onelab.hook.applications;

final class BiliInternationalPolicy {
    static final String LARGE_SCREEN_KEY = "dd_screen_adjust_xiaomi_864";

    private BiliInternationalPolicy() {
    }

    static Object rewriteConfigValue(String key, Object value, boolean enabled) {
        if (enabled && LARGE_SCREEN_KEY.equals(key) && "off".equals(value)) {
            return "large";
        }
        return value;
    }
}
