package io.github.pigerzhu.onelab.hook.system;

/** Pure decisions for exposing a custom ratio through Samsung's user-ratio state machine. */
final class AspectRatioFullscreenPolicy {
    private static final int DEFAULT_CODE = 0;
    private static final int FULLSCREEN_CODE = 6;
    private static final int SYNTHETIC_RATIO_CODE = 4;

    private AspectRatioFullscreenPolicy() {
    }

    static int syntheticUserCode(float ratio, int originalCode) {
        if (!isValidRatio(ratio)) {
            return originalCode;
        }
        if (originalCode == FULLSCREEN_CODE
                || (originalCode != DEFAULT_CODE && originalCode != 7)) {
            return originalCode;
        }
        return SYNTHETIC_RATIO_CODE;
    }

    static boolean shouldApplyCustomRatio(float ratio, int originalCode) {
        return isValidRatio(ratio) && originalCode != FULLSCREEN_CODE;
    }

    static boolean isValidRatio(float ratio) {
        return Float.isFinite(ratio) && ratio > 1.0f;
    }
}
