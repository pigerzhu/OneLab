package io.github.pigerzhu.onelab.contract;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GpuFrequencyRangeTest {
    private static final int[] FREQUENCIES = {
            80, 155, 231, 310, 366, 422, 500, 578,
            629, 680, 720, 770, 834, 903, 950, 1000
    };

    @Test
    public void normalizeSnapsToSupportedFrequencies() {
        GpuFrequencyRange range = GpuFrequencyRange.normalize(400, 910, FREQUENCIES);

        assertEquals(422, range.minMhz());
        assertEquals(903, range.maxMhz());
    }

    @Test
    public void normalizeKeepsMinimumAtOrBelowMaximum() {
        GpuFrequencyRange range = GpuFrequencyRange.normalize(950, 500, FREQUENCIES);

        assertEquals(950, range.minMhz());
        assertEquals(950, range.maxMhz());
        assertTrue(range.isLocked());
    }

    @Test
    public void equalEndpointsRepresentLock() {
        assertTrue(GpuFrequencyRange.normalize(422, 422, FREQUENCIES).isLocked());
    }

    @Test
    public void normalizeUsesDeviceFrequencyLevels() {
        GpuFrequencyRange range = GpuFrequencyRange.normalize(
                400, 900, new int[]{300, 600, 800, 1100});

        assertEquals(300, range.minMhz());
        assertEquals(800, range.maxMhz());
    }
}
