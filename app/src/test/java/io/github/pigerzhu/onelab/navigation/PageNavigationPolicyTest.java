package io.github.pigerzhu.onelab.navigation;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class PageNavigationPolicyTest {
    @Test
    public void nestedReturnCompletesBackAnimationOnLargeScreen() {
        assertEquals(-1, PageNavigationPolicy.direction(true, true));
    }

    @Test
    public void forwardNavigationKeepsLayoutSpecificDirection() {
        assertEquals(0, PageNavigationPolicy.direction(false, true));
        assertEquals(1, PageNavigationPolicy.direction(false, false));
    }
}
