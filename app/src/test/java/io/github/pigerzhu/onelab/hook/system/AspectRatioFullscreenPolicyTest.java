package io.github.pigerzhu.onelab.hook.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AspectRatioFullscreenPolicyTest {
    @Test
    public void customRatioGetsSyntheticUserRatioCode() {
        assertEquals(4, AspectRatioFullscreenPolicy.syntheticUserCode(2.0f, 0));
        assertEquals(4, AspectRatioFullscreenPolicy.syntheticUserCode(1.7778f, 0));
        assertEquals(4, AspectRatioFullscreenPolicy.syntheticUserCode(1.3333f, 0));
    }

    @Test
    public void explicitSystemStatesArePreserved() {
        assertEquals(6, AspectRatioFullscreenPolicy.syntheticUserCode(2.0f, 6));
        assertEquals(3, AspectRatioFullscreenPolicy.syntheticUserCode(2.0f, 3));
        assertEquals(4, AspectRatioFullscreenPolicy.syntheticUserCode(2.0f, 4));
    }

    @Test
    public void fullscreenDisablesCustomRatio() {
        assertTrue(AspectRatioFullscreenPolicy.shouldApplyCustomRatio(2.0f, 0));
        assertFalse(AspectRatioFullscreenPolicy.shouldApplyCustomRatio(2.0f, 6));
    }

    @Test
    public void invalidRatioDoesNotCreateOverride() {
        assertEquals(0, AspectRatioFullscreenPolicy.syntheticUserCode(1.0f, 0));
        assertEquals(0, AspectRatioFullscreenPolicy.syntheticUserCode(Float.NaN, 0));
        assertFalse(AspectRatioFullscreenPolicy.shouldApplyCustomRatio(1.0f, 0));
    }
}
