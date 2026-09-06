package io.github.pigerzhu.onelab.hook.system;

final class SiopRefreshRatePolicy {
    private static final String SIOP_LIMITER_TAG = "SiopLimiterHrr";

    private SiopRefreshRatePolicy() {
    }

    static boolean shouldBlock(boolean enabled, String tag) {
        return enabled && SIOP_LIMITER_TAG.equals(tag);
    }
}
