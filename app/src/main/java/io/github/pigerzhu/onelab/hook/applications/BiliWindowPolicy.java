package io.github.pigerzhu.onelab.hook.applications;

final class BiliWindowPolicy {
    private static final int FOLD_LANDSCAPE_MIN_WIDTH_DP = 800;
    private static final int FOLD_LANDSCAPE_MIN_HEIGHT_DP = 600;

    private BiliWindowPolicy() {
    }

    static boolean shouldPromoteLandscape(int widthDp, int heightDp) {
        return widthDp > heightDp
                && widthDp >= FOLD_LANDSCAPE_MIN_WIDTH_DP
                && heightDp >= FOLD_LANDSCAPE_MIN_HEIGHT_DP;
    }
}
