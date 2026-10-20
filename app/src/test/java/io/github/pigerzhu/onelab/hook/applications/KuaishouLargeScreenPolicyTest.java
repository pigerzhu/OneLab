package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class KuaishouLargeScreenPolicyTest {
    @Test
    public void acceptsUnfoldedFoldInBothOrientations() {
        assertTrue(KuaishouLargeScreenPolicy.isLargeWindow(707, 823));
        assertTrue(KuaishouLargeScreenPolicy.isLargeWindow(823, 707));
    }

    @Test
    public void rejectsCoverScreenAndNarrowMultiWindow() {
        assertFalse(KuaishouLargeScreenPolicy.isLargeWindow(345, 823));
        assertFalse(KuaishouLargeScreenPolicy.isLargeWindow(590, 823));
        assertFalse(KuaishouLargeScreenPolicy.isLargeWindow(823, 590));
    }
}
