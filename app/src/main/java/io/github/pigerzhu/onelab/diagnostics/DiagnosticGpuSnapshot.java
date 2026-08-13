package io.github.pigerzhu.onelab.diagnostics;

import io.github.pigerzhu.onelab.contract.GpuFrequencyTable;

/** Converts the boot-scoped SDHMS GPU table into concise diagnostic evidence. */
final class DiagnosticGpuSnapshot {
    private DiagnosticGpuSnapshot() {}

    static String describe(String snapshot, int currentBootCount) {
        if (snapshot == null || snapshot.trim().isEmpty()) {
            return output("unavailable", "unset", currentBootCount, new int[0]);
        }
        int separator = snapshot.indexOf(':');
        if (separator <= 0) {
            return output("invalid", "invalid", currentBootCount, new int[0]);
        }
        int snapshotBootCount;
        try {
            snapshotBootCount = Integer.parseInt(snapshot.substring(0, separator));
        } catch (NumberFormatException ignored) {
            return output("invalid", "invalid", currentBootCount, new int[0]);
        }
        if (snapshotBootCount != currentBootCount) {
            return output("stale", String.valueOf(snapshotBootCount),
                    currentBootCount, new int[0]);
        }
        int[] frequencies = GpuFrequencyTable.parse(snapshot.substring(separator + 1));
        String status;
        if (GpuFrequencyTable.isUsable(frequencies)) {
            status = "usable";
        } else if (frequencies.length == 1) {
            status = "insufficient";
        } else {
            status = "invalid";
        }
        return output(status, String.valueOf(snapshotBootCount),
                currentBootCount, frequencies);
    }

    private static String output(
            String status,
            String snapshotBootCount,
            int currentBootCount,
            int[] frequencies
    ) {
        StringBuilder result = new StringBuilder();
        result.append("snapshot_status=").append(status).append('\n');
        result.append("snapshot_boot_count=").append(snapshotBootCount).append('\n');
        result.append("current_boot_count=").append(currentBootCount).append('\n');
        result.append("level_count=").append(frequencies.length).append('\n');
        if (frequencies.length > 0) {
            result.append("minimum_mhz=").append(frequencies[0]).append('\n');
            result.append("maximum_mhz=")
                    .append(frequencies[frequencies.length - 1]).append('\n');
            result.append("levels_mhz=")
                    .append(GpuFrequencyTable.serialize(frequencies)).append('\n');
        }
        return result.toString();
    }
}
