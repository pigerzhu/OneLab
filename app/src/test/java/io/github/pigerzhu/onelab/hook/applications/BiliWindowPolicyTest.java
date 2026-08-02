package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BiliWindowPolicyTest {
    @Test
    public void promotesFoldLandscapeBelowBilibiliDefaultBreakpoint() {
        assertTrue(BiliWindowPolicy.shouldPromoteLandscape(823, 707));
    }

    @Test
    public void keepsPortraitOnOriginalClassification() {
        assertFalse(BiliWindowPolicy.shouldPromoteLandscape(707, 823));
    }

    @Test
    public void keepsCoverAndSmallWindowsOnOriginalClassification() {
        assertFalse(BiliWindowPolicy.shouldPromoteLandscape(799, 700));
        assertFalse(BiliWindowPolicy.shouldPromoteLandscape(900, 599));
    }
}
