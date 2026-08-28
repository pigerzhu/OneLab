package io.github.pigerzhu.onelab.contract;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Serialization of the per-app refresh-rate policy stored under
 * {@code onelab_refresh_rate_overrides}. The {@code package:mode:min:max} format is a
 * user-visible contract: diagnostics, manual settings edits and existing installs all
 * depend on it, so the accepted shapes must not change.
 */
public final class RefreshRateOverrides {
    public static final int MODE_HIGH_REFRESH_BYPASS = 1;
    public static final int MODE_FIXED = 2;
    public static final int MODE_RANGE = 3;

    private RefreshRateOverrides() {
    }

    public static Map<String, RefreshRateOverride> parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, RefreshRateOverride> parsed = new LinkedHashMap<>();
        for (String entry : raw.split(";")) {
            String[] parts = entry.split(":");
            if (parts.length < 2 || parts[0].trim().isEmpty()) {
                continue;
            }
            try {
                int mode = Integer.parseInt(parts[1].trim());
                float min = parts.length > 2 ? Float.parseFloat(parts[2].trim()) : 0f;
                float max = parts.length > 3 ? Float.parseFloat(parts[3].trim()) : min;
                if (mode == MODE_HIGH_REFRESH_BYPASS
                        || (mode == MODE_FIXED && (min == -1f || min > 0f))
                        || (mode == MODE_RANGE && min > 0f && max >= min)) {
                    parsed.put(parts[0].trim(), new RefreshRateOverride(mode, min, max));
                }
            } catch (NumberFormatException ignored) {
                // Keep valid entries active when one manually edited entry is malformed.
            }
        }
        return parsed.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(parsed);
    }

    public static String serialize(Map<String, RefreshRateOverride> values) {
        StringBuilder value = new StringBuilder();
        for (Map.Entry<String, RefreshRateOverride> entry : values.entrySet()) {
            if (value.length() > 0) value.append(';');
            RefreshRateOverride item = entry.getValue();
            value.append(entry.getKey()).append(':').append(item.mode).append(':')
                    .append(String.format(Locale.US, "%.2f", item.min)).append(':')
                    .append(String.format(Locale.US, "%.2f", item.max));
        }
        return value.toString();
    }
}
