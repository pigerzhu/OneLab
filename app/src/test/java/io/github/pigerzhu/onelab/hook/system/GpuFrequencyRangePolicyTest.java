package io.github.pigerzhu.onelab.hook.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GpuFrequencyRangePolicyTest {
    @Test
    public void rangeVotesRequireThermalSiopAndExperimentSwitches() {
        assertTrue(GpuFrequencyRangePolicy.shouldApply(true, true, true));
        assertFalse(GpuFrequencyRangePolicy.shouldApply(false, true, true));
        assertFalse(GpuFrequencyRangePolicy.shouldApply(true, false, true));
        assertFalse(GpuFrequencyRangePolicy.shouldApply(true, true, false));
    }

    @Test
    public void siopGpuBypassAlwaysReleasesToHighestSupportedFrequency() {
        assertEquals(1000, GpuFrequencyRangePolicy.siopReleaseMhz());
        assertEquals(1000, GpuFrequencyRangePolicy.rewriteGpuCap(500));
        assertEquals(-1, GpuFrequencyRangePolicy.rewriteGpuCap(-1));
    }
}
