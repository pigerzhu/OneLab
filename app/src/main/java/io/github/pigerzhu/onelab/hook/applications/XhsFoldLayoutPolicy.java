package io.github.pigerzhu.onelab.hook.applications;

final class XhsFoldLayoutPolicy {
    private static final int LARGE_SCREEN_MIN_DP = 600;

    private XhsFoldLayoutPolicy() {
    }

    static boolean isVideoLayoutEligible(boolean enabled, int smallestScreenWidthDp) {
        return enabled && smallestScreenWidthDp >= LARGE_SCREEN_MIN_DP;
    }
}
