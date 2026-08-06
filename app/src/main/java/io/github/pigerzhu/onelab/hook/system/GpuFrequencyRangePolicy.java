package io.github.pigerzhu.onelab.hook.system;

import io.github.pigerzhu.onelab.contract.SettingsKeys;

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

    public static int siopReleaseMhz() {
        return SettingsKeys.SDHMS_GPU_FREQS_MHZ[
                SettingsKeys.SDHMS_GPU_FREQS_MHZ.length - 1];
    }

    public static int rewriteGpuCap(int requestedMhz) {
        if (requestedMhz <= 0) return requestedMhz;
        return Math.max(requestedMhz, siopReleaseMhz());
    }
}
