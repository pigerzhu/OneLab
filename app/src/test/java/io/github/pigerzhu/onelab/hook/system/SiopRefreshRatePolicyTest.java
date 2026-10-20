package io.github.pigerzhu.onelab.hook.system;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SiopRefreshRatePolicyTest {
    @Test
    public void blocksOnlySiopLimiterHrrWhenHardBypassEnabled() {
        assertTrue(SiopRefreshRatePolicy.shouldBlock(true, "SiopLimiterHrr"));
        assertFalse(SiopRefreshRatePolicy.shouldBlock(false, "SiopLimiterHrr"));
        assertFalse(SiopRefreshRatePolicy.shouldBlock(true, "OtherLimiter"));
        assertFalse(SiopRefreshRatePolicy.shouldBlock(true, null));
    }
}
