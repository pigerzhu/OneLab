package io.github.pigerzhu.onelab.contract;

import java.util.Arrays;
import java.util.TreeSet;

/** Device-reported GPU levels shared between the SDHMS hook and OneLab UI. */
public final class GpuFrequencyTable {
    private GpuFrequencyTable() {
    }

    public static int[] common(int[] minimumLevels, int[] maximumLevels) {
        if (minimumLevels == null || maximumLevels == null) return new int[0];
        TreeSet<Integer> maximum = new TreeSet<>();
        for (int frequency : maximumLevels) {
            if (frequency > 0) maximum.add(frequency);
        }
        TreeSet<Integer> common = new TreeSet<>();
        for (int frequency : minimumLevels) {
            if (frequency > 0 && maximum.contains(frequency)) common.add(frequency);
        }
        int[] result = new int[common.size()];
        int index = 0;
        for (int frequency : common) result[index++] = frequency;
        return result;
    }

    public static String serialize(int[] frequencies) {
        if (frequencies == null || frequencies.length == 0) return "";
        StringBuilder result = new StringBuilder();
        for (int frequency : frequencies) {
            if (result.length() > 0) result.append(',');
            result.append(frequency);
        }
        return result.toString();
    }

    public static int[] parse(String snapshot) {
        if (snapshot == null || snapshot.trim().isEmpty()) return new int[0];
        String[] parts = snapshot.split(",", -1);
        int[] parsed = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                parsed[i] = Integer.parseInt(parts[i].trim());
                if (parsed[i] <= 0) return new int[0];
            }
        } catch (NumberFormatException ignored) {
            return new int[0];
        }
        int[] sorted = Arrays.copyOf(parsed, parsed.length);
        Arrays.sort(sorted);
        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i] == sorted[i - 1]) return new int[0];
        }
        return sorted;
    }

    public static String serializeSnapshot(int bootCount, int[] frequencies) {
        return bootCount + ":" + serialize(frequencies);
    }

    public static int[] parseSnapshot(String snapshot, int currentBootCount) {
        if (snapshot == null) return new int[0];
        int separator = snapshot.indexOf(':');
        if (separator <= 0) return new int[0];
        try {
            if (Integer.parseInt(snapshot.substring(0, separator)) != currentBootCount) {
                return new int[0];
            }
        } catch (NumberFormatException ignored) {
            return new int[0];
        }
        return parse(snapshot.substring(separator + 1));
    }

    public static boolean isUsable(int[] frequencies) {
        return frequencies != null && frequencies.length >= 2;
    }
}
