package io.github.pigerzhu.onelab.hook.system;

/**
 * Pure merge rules for the screen-level refresh-rate range. No Android or Xposed types
 * so the whole contract stays unit-testable on the JVM.
 *
 * The screen range is a boundary, not a second policy: an application policy decides
 * what the app wants, the screen range decides what the current panel ultimately allows.
 */
public final class RefreshRateScreenRangePolicy {
    public static final int PANEL_UNKNOWN = 0;
    public static final int PANEL_INNER = 1;
    public static final int PANEL_OUTER = 2;

    /** Same input bound as the per-app refresh-rate editor; panels rarely exceed 240 Hz. */
    public static final float MAX_RATE = 240f;
    /**
     * Samsung firmwares expose modes such as 119.99 Hz (their own code compares against
     * 119.99f), so range membership needs a small tolerance instead of exact equality.
     */
    public static final float RATE_TOLERANCE = 0.5f;

    private RefreshRateScreenRangePolicy() {
    }

    /**
     * Maps a Samsung device-state name onto the panel that owns the front application.
     * State names are stable business strings (verified on Fold6 One UI 8:
     * CLOSED/TENT/HALF_OPENED/OPENED/CONCURRENT_INNER_DEFAULT/CONCURRENT_OUTER_DEFAULT);
     * numeric identifiers are deliberately not used because they are not contractual.
     */
    public static int panelForStateName(String name) {
        if (name == null || name.isEmpty()) return PANEL_UNKNOWN;
        if (name.contains("CLOSE") || name.contains("TENT")) return PANEL_OUTER;
        if (name.contains("OPEN")) return PANEL_INNER;
        // HALF_FOLDED is the HALF_OPENED synonym used by some firmware generations;
        // a bare "FOLDED" stays unknown because its meaning is not contractual.
        if (name.contains("HALF_FOLDED")) return PANEL_INNER;
        if (name.contains("OUTER")) return PANEL_OUTER;
        if (name.contains("INNER")) return PANEL_INNER;
        return PANEL_UNKNOWN;
    }

    public static boolean isValidScreenRange(float min, float max) {
        return Float.isFinite(min) && Float.isFinite(max)
                && min > 0f && max <= MAX_RATE && min <= max;
    }

    /**
     * Parses one screen-side configuration. Returns {@code null} unless the switch is
     * explicitly enabled and the bounds are valid, so a malformed or disabled side
     * simply keeps system behavior.
     */
    public static float[] parseScreenRange(String enabledRaw, String minRaw, String maxRaw) {
        if (!"1".equals(enabledRaw == null ? null : enabledRaw.trim())) return null;
        Float min = parseRate(minRaw);
        Float max = parseRate(maxRaw);
        if (min == null || max == null) return null;
        return isValidScreenRange(min, max) ? new float[]{min, max} : null;
    }

    private static Float parseRate(String raw) {
        if (raw == null) return null;
        try {
            float value = Float.parseFloat(raw.trim());
            return Float.isFinite(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** Returns the range configured for the given panel, or null when unknown/disabled. */
    public static float[] rangeForPanel(int panel, float[] innerRange, float[] outerRange) {
        if (panel == PANEL_INNER) return innerRange;
        if (panel == PANEL_OUTER) return outerRange;
        return null;
    }

    /**
     * Endpoint clamp: {@code effectiveMin = clamp(appMin)}, {@code effectiveMax =
     * clamp(appMax)}. App bands fully above the screen range collapse to the screen
     * maximum, bands fully below collapse to the screen minimum, overlapping bands
     * produce the intersection. Only inverted input (appMin > appMax) needs the
     * midpoint fallback because clamping alone cannot order it.
     */
    public static float[] merge(float appMin, float appMax, float screenMin, float screenMax) {
        float effectiveMin = clamp(appMin, screenMin, screenMax);
        float effectiveMax = clamp(appMax, screenMin, screenMax);
        if (effectiveMin > effectiveMax) {
            float target = clamp((appMin + appMax) / 2f, screenMin, screenMax);
            effectiveMin = target;
            effectiveMax = target;
        }
        return new float[]{effectiveMin, effectiveMax};
    }

    /**
     * Merges the aggregated display request with the screen range. Unset votes
     * ({@code <= 0}) mean "no preference", which the screen range replaces with its own
     * bound; set votes are endpoint-clamped into the screen range.
     */
    public static float[] mergeDisplayRequest(
            float givenMin, float givenMax, float screenMin, float screenMax) {
        float appMin = givenMin > 0f ? givenMin : screenMin;
        float appMax = givenMax > 0f ? givenMax : screenMax;
        return merge(appMin, appMax, screenMin, screenMax);
    }

    /** Clamps one requested rate into the screen range; unset rates pass through. */
    public static float clampRequest(float rate, float screenMin, float screenMax) {
        if (rate <= 0f) return rate;
        return clamp(rate, screenMin, screenMax);
    }

    public static boolean withinRange(float rate, float min, float max) {
        return rate >= min - RATE_TOLERANCE && rate <= max + RATE_TOLERANCE;
    }

    /**
     * Picks the supported mode closest to {@code targetRate} among the modes inside the
     * screen range, preferring {@code currentModeId} on distance ties. Returns -1 when
     * no supported mode can satisfy the range, so the caller can keep Samsung's choice.
     */
    public static int pickModeId(
            int[] modeIds, float[] rates, float targetRate, float min, float max,
            int currentModeId) {
        if (modeIds == null || rates == null || modeIds.length != rates.length) {
            return -1;
        }
        int bestIndex = -1;
        float bestDistance = Float.POSITIVE_INFINITY;
        for (int index = 0; index < modeIds.length; index++) {
            float rate = rates[index];
            if (!withinRange(rate, min, max)) continue;
            float distance = Math.abs(rate - targetRate);
            boolean preferred = distance < bestDistance
                    || (distance == bestDistance && modeIds[index] == currentModeId);
            if (preferred) {
                bestDistance = distance;
                bestIndex = index;
            }
        }
        return bestIndex >= 0 ? modeIds[bestIndex] : -1;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }
}
