package io.github.pigerzhu.onelab.hook.applications;

final class BiliWindowPolicy {
    static final int TYPE_UNCHANGED = 0;
    static final int TYPE_LARGE_PORTRAIT = 2;
    static final int TYPE_LARGE_LANDSCAPE = 3;

    private static final int FOLD_LANDSCAPE_MIN_WIDTH_DP = 800;
    private static final int FOLD_LANDSCAPE_MIN_HEIGHT_DP = 600;

    private BiliWindowPolicy() {
    }

    static int tabletWindowType(int widthDp, int heightDp) {
        if (widthDp > heightDp
                && widthDp >= FOLD_LANDSCAPE_MIN_WIDTH_DP
                && heightDp >= FOLD_LANDSCAPE_MIN_HEIGHT_DP) {
            return TYPE_LARGE_LANDSCAPE;
        }
        if (heightDp >= widthDp
                && heightDp >= FOLD_LANDSCAPE_MIN_WIDTH_DP
                && widthDp >= FOLD_LANDSCAPE_MIN_HEIGHT_DP) {
            return TYPE_LARGE_PORTRAIT;
        }
        return TYPE_UNCHANGED;
    }
}
