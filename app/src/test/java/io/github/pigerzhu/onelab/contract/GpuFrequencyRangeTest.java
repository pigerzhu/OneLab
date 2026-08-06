package io.github.pigerzhu.onelab.contract;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GpuFrequencyRangeTest {
    @Test
    public void normalizeSnapsToSupportedFrequencies() {
        GpuFrequencyRange range = GpuFrequencyRange.normalize(400, 910);

        assertEquals(422, range.minMhz());
        assertEquals(903, range.maxMhz());
    }

    @Test
    public void normalizeKeepsMinimumAtOrBelowMaximum() {
        GpuFrequencyRange range = GpuFrequencyRange.normalize(950, 500);

        assertEquals(950, range.minMhz());
        assertEquals(950, range.maxMhz());
        assertTrue(range.isLocked());
    }

    @Test
    public void equalEndpointsRepresentLock() {
        assertTrue(GpuFrequencyRange.normalize(422, 422).isLocked());
    }
}
