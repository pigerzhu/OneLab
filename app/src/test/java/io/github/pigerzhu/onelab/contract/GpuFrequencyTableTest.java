package io.github.pigerzhu.onelab.contract;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public final class GpuFrequencyTableTest {
    @Test
    public void commonFrequenciesAreSortedAndDeduplicated() {
        int[] frequencies = GpuFrequencyTable.common(
                new int[]{770, 231, 500, 231},
                new int[]{500, 770, 950, 231, 500});

        assertArrayEquals(new int[]{231, 500, 770}, frequencies);
    }

    @Test
    public void serializedSnapshotRoundTrips() {
        String snapshot = GpuFrequencyTable.serialize(new int[]{155, 231, 500});

        assertArrayEquals(new int[]{155, 231, 500}, GpuFrequencyTable.parse(snapshot));
    }

    @Test
    public void malformedSnapshotIsUnavailable() {
        assertArrayEquals(new int[0], GpuFrequencyTable.parse("155,broken,500"));
    }

    @Test
    public void bootScopedSnapshotRejectsPreviousBoot() {
        String snapshot = GpuFrequencyTable.serializeSnapshot(42, new int[]{155, 500});

        assertArrayEquals(new int[]{155, 500},
                GpuFrequencyTable.parseSnapshot(snapshot, 42));
        assertArrayEquals(new int[0], GpuFrequencyTable.parseSnapshot(snapshot, 43));
    }
}
