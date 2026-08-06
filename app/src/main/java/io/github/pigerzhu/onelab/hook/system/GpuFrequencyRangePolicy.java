package io.github.pigerzhu.onelab.hook.system;

import io.github.pigerzhu.onelab.contract.GpuFrequencyRange;

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

    public static int siopFloorMhz(
            int configuredFloorMhz,
            boolean thermalEnabled,
            boolean perfCapBypassEnabled,
            boolean rangeExperimentEnabled,
            GpuFrequencyRange range
    ) {
        if (!shouldApply(thermalEnabled, perfCapBypassEnabled, rangeExperimentEnabled)) {
            return configuredFloorMhz;
        }
        return Math.max(configuredFloorMhz, range.maxMhz());
    }
}
