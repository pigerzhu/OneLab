package io.github.pigerzhu.onelab.hook.applications;

final class KuaishouLargeScreenPolicy {
    private KuaishouLargeScreenPolicy() {
    }

    static boolean isLargeWindow(int widthDp, int heightDp) {
        return widthDp >= 600 && heightDp >= 600;
    }
}
