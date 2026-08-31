package io.github.pigerzhu.onelab.hook.applications;

final class QishuiLargeScreenPolicy {
    private static final int LARGE_WINDOW_MIN_DP = 600;

    private QishuiLargeScreenPolicy() {
    }

    static boolean isLargeWindow(int widthDp, int heightDp) {
        return widthDp >= LARGE_WINDOW_MIN_DP && heightDp >= LARGE_WINDOW_MIN_DP;
    }

    static boolean shouldForcePad(boolean enabled, int widthDp, int heightDp) {
        return enabled && isLargeWindow(widthDp, heightDp);
    }

    static boolean shouldForcePlayerLayout(
            boolean padEnabled,
            boolean playerLayoutEnabled,
            int widthDp,
            int heightDp) {
        return padEnabled && playerLayoutEnabled && isLargeWindow(widthDp, heightDp);
    }
}
