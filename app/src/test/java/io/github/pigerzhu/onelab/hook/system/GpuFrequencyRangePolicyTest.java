package io.github.pigerzhu.onelab.hook.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.github.pigerzhu.onelab.contract.GpuFrequencyRange;
import org.junit.Test;

public final class GpuFrequencyRangePolicyTest {
    private static final GpuFrequencyRange RANGE =
            GpuFrequencyRange.normalize(310, 770);

    @Test
    public void rangeVotesRequireThermalSiopAndExperimentSwitches() {
        assertTrue(GpuFrequencyRangePolicy.shouldApply(true, true, true));
        assertFalse(GpuFrequencyRangePolicy.shouldApply(false, true, true));
        assertFalse(GpuFrequencyRangePolicy.shouldApply(true, false, true));
        assertFalse(GpuFrequencyRangePolicy.shouldApply(true, true, false));
    }

    @Test
    public void activeRangeMakesSiopReleaseAtLeastItsMaximum() {
        assertEquals(770, GpuFrequencyRangePolicy.siopFloorMhz(
                500, true, true, true, RANGE));
        assertEquals(903, GpuFrequencyRangePolicy.siopFloorMhz(
                903, true, true, true, RANGE));
    }

    @Test
    public void inactiveRangeDoesNotChangeExistingSiopFloor() {
        assertEquals(500, GpuFrequencyRangePolicy.siopFloorMhz(
                500, true, true, false, RANGE));
    }
}
