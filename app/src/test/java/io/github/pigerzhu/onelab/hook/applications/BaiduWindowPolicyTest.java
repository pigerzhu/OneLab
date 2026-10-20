package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BaiduWindowPolicyTest {
    @Test
    public void acceptsUnfoldedFoldInBothOrientations() {
        assertTrue(BaiduWindowPolicy.isLargeWindow(707, 823));
        assertTrue(BaiduWindowPolicy.isLargeWindow(823, 707));
    }

    @Test
    public void rejectsCoverScreenAndNarrowMultiWindow() {
        assertFalse(BaiduWindowPolicy.isLargeWindow(345, 823));
        assertFalse(BaiduWindowPolicy.isLargeWindow(590, 823));
        assertFalse(BaiduWindowPolicy.isLargeWindow(823, 590));
    }
}
