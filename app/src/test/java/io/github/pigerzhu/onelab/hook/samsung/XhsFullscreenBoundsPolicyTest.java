package io.github.pigerzhu.onelab.hook.samsung;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public final class XhsFullscreenBoundsPolicyTest {
    @Test
    public void usesTaskBoundsWhileViewerIsVisible() {
        int[] task = {0, 0, 1856, 2160};
        int[] right = {742, 0, 1856, 2160};
        assertArrayEquals(task, XhsFullscreenBoundsPolicy.targetBounds(task, right, true));
    }

    @Test
    public void restoresCapturedRightBoundsAfterViewerCloses() {
        int[] task = {0, 0, 1856, 2160};
        int[] right = {742, 0, 1856, 2160};
        assertArrayEquals(right, XhsFullscreenBoundsPolicy.targetBounds(task, right, false));
    }
}
