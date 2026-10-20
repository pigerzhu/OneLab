package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public final class BiliWindowPolicyTest {
    @Test
    public void classifiesFoldLandscapeAsLargeLandscape() {
        assertEquals(
                BiliWindowPolicy.TYPE_LARGE_LANDSCAPE,
                BiliWindowPolicy.tabletWindowType(823, 707));
    }

    @Test
    public void classifiesFoldPortraitAsLargePortrait() {
        assertEquals(
                BiliWindowPolicy.TYPE_LARGE_PORTRAIT,
                BiliWindowPolicy.tabletWindowType(707, 823));
    }

    @Test
    public void keepsCoverAndSmallWindowsOnOriginalClassification() {
        assertEquals(
                BiliWindowPolicy.TYPE_UNCHANGED,
                BiliWindowPolicy.tabletWindowType(799, 599));
        assertEquals(
                BiliWindowPolicy.TYPE_UNCHANGED,
                BiliWindowPolicy.tabletWindowType(599, 900));
    }

    @Test
    public void promotesEligibleLandscapeToNativeLargeThreshold() {
        assertArrayEquals(
                new int[]{840, 707},
                BiliWindowPolicy.promotedDimensions(823, 707));
    }

    @Test
    public void promotesEligiblePortraitToNativeLargeThreshold() {
        assertArrayEquals(
                new int[]{707, 840},
                BiliWindowPolicy.promotedDimensions(707, 823));
    }

    @Test
    public void leavesIneligibleWindowsUnchanged() {
        assertArrayEquals(
                new int[]{799, 599},
                BiliWindowPolicy.promotedDimensions(799, 599));
    }
}
