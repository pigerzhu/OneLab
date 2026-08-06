package io.github.pigerzhu.onelab.contract;

/** Normalized GPU frequency range shared by UI and hook processes. */
public final class GpuFrequencyRange {
    private final int minMhz;
    private final int maxMhz;

    private GpuFrequencyRange(int minMhz, int maxMhz) {
        this.minMhz = minMhz;
        this.maxMhz = maxMhz;
    }

    public static GpuFrequencyRange normalize(int requestedMinMhz, int requestedMaxMhz) {
        int min = nearestSupported(requestedMinMhz);
        int max = nearestSupported(requestedMaxMhz);
        if (min > max) {
            max = min;
        }
        return new GpuFrequencyRange(min, max);
    }

    public int minMhz() {
        return minMhz;
    }

    public int maxMhz() {
        return maxMhz;
    }

    public boolean isLocked() {
        return minMhz == maxMhz;
    }

    private static int nearestSupported(int requestedMhz) {
        int nearest = SettingsKeys.SDHMS_GPU_FREQS_MHZ[0];
        int nearestDistance = Math.abs(requestedMhz - nearest);
        for (int frequency : SettingsKeys.SDHMS_GPU_FREQS_MHZ) {
            int distance = Math.abs(requestedMhz - frequency);
            if (distance < nearestDistance
                    || (distance == nearestDistance && frequency > nearest)) {
                nearest = frequency;
                nearestDistance = distance;
            }
        }
        return nearest;
    }
}
