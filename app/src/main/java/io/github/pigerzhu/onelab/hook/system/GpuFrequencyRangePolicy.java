package io.github.pigerzhu.onelab.hook.system;

import io.github.pigerzhu.onelab.contract.GpuFrequencyTable;

/** Coordinates the optional GPU range votes with the existing SDHMS bypass. */
public final class GpuFrequencyRangePolicy {
    private GpuFrequencyRangePolicy() {
    }

    public static boolean shouldApply(
            boolean thermalEnabled,
            boolean perfCapBypassEnabled,
            boolean rangeExperimentEnabled
    ) {
        return thermalEnabled && perfCapBypassEnabled && rangeExperimentEnabled;
    }

    public static int rewriteGpuCap(int requestedMhz, int[] supportedFrequencies) {
        if (requestedMhz <= 0) return requestedMhz;
        if (!GpuFrequencyTable.isUsable(supportedFrequencies)) {
            return requestedMhz;
        }
        return Math.max(requestedMhz,
                supportedFrequencies[supportedFrequencies.length - 1]);
    }
}
