package io.github.pigerzhu.onelab.hook.applications;

final class BaiduWindowPolicy {
    private static final int LARGE_WINDOW_MIN_DP = 600;

    private BaiduWindowPolicy() {
    }

    static boolean isLargeWindow(int widthDp, int heightDp) {
        return widthDp >= LARGE_WINDOW_MIN_DP && heightDp >= LARGE_WINDOW_MIN_DP;
    }
}
