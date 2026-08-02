package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertEquals;

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
}
