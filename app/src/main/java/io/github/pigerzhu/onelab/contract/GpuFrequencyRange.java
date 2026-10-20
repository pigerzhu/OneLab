package io.github.pigerzhu.onelab.contract;

/** Normalized GPU frequency range shared by UI and hook processes. */
public final class GpuFrequencyRange {
    private final int minMhz;
    private final int maxMhz;

    private GpuFrequencyRange(int minMhz, int maxMhz) {
        this.minMhz = minMhz;
        this.maxMhz = maxMhz;
    }

    public static GpuFrequencyRange normalize(
            int requestedMinMhz,
            int requestedMaxMhz,
            int[] supportedFrequencies
    ) {
        if (!GpuFrequencyTable.isUsable(supportedFrequencies)) {
            throw new IllegalArgumentException("GPU frequency table is unavailable");
        }
        int min = nearestSupported(requestedMinMhz, supportedFrequencies);
        int max = nearestSupported(requestedMaxMhz, supportedFrequencies);
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

    private static int nearestSupported(int requestedMhz, int[] supportedFrequencies) {
        int nearest = supportedFrequencies[0];
        int nearestDistance = Math.abs(requestedMhz - nearest);
        for (int frequency : supportedFrequencies) {
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
