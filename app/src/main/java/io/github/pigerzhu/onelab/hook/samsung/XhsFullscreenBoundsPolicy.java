package io.github.pigerzhu.onelab.hook.samsung;

final class XhsFullscreenBoundsPolicy {
    private XhsFullscreenBoundsPolicy() {
    }

    static int[] targetBounds(int[] taskBounds, int[] capturedRightBounds, boolean enabled) {
        int[] source = enabled ? taskBounds : capturedRightBounds;
        return source == null || source.length != 4 ? null : source.clone();
    }
}
