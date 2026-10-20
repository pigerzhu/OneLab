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
        int[] frequencies = {300, 600, 1100};

        assertEquals(1100, GpuFrequencyRangePolicy.rewriteGpuCap(500, frequencies));
        assertEquals(-1, GpuFrequencyRangePolicy.rewriteGpuCap(-1, frequencies));
    }

    @Test
    public void siopGpuBypassFailsOpenWithoutDeviceFrequencies() {
        assertEquals(500, GpuFrequencyRangePolicy.rewriteGpuCap(500, new int[0]));
        assertEquals(500, GpuFrequencyRangePolicy.rewriteGpuCap(500, new int[]{600}));
    }
}
