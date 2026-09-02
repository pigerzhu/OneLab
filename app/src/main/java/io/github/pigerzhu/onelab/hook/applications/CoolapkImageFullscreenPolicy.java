package io.github.pigerzhu.onelab.hook.applications;

final class CoolapkImageFullscreenPolicy {
    static final String TARGET_PACKAGE = "com.coolapk.market";
    static final String TARGET_ACTIVITY =
            "com.coolapk.market.view.photo.PhotoViewV16Activity";

    private CoolapkImageFullscreenPolicy() {
    }

    static boolean isEnabled(String masterValue, String appValue) {
        return "1".equals(masterValue) && "1".equals(appValue);
    }
}
