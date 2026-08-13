package io.github.pigerzhu.onelab.hook.applications;

final class LarkPaneWidthPolicy {
    private LarkPaneWidthPolicy() {
    }

    static int width(int totalWidth, float ratio, boolean leftPane) {
        return Math.round(totalWidth * (leftPane ? ratio : 1f - ratio));
    }
}
