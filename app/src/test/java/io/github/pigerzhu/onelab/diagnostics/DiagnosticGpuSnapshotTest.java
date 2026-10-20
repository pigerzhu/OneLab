package io.github.pigerzhu.onelab.diagnostics;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DiagnosticGpuSnapshotTest {
    @Test
    public void usableSnapshotReportsDeviceFrequencyRange() {
        String report = DiagnosticGpuSnapshot.describe("42:155,231,500,770", 42);

        assertTrue(report.contains("snapshot_status=usable"));
        assertTrue(report.contains("level_count=4"));
        assertTrue(report.contains("minimum_mhz=155"));
        assertTrue(report.contains("maximum_mhz=770"));
    }

    @Test
    public void previousBootSnapshotIsReportedAsStale() {
        String report = DiagnosticGpuSnapshot.describe("41:155,770", 42);

        assertTrue(report.contains("snapshot_status=stale"));
        assertTrue(report.contains("snapshot_boot_count=41"));
        assertTrue(report.contains("level_count=0"));
    }

    @Test
    public void singleFrequencyIsReportedAsInsufficient() {
        String report = DiagnosticGpuSnapshot.describe("42:770", 42);

        assertTrue(report.contains("snapshot_status=insufficient"));
        assertTrue(report.contains("level_count=1"));
    }
}
