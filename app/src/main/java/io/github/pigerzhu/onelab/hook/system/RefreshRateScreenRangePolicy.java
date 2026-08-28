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

    /** Samsung maps the active panel onto logical display 0 across fold states. */
    public static final int MAIN_LOGICAL_DISPLAY_ID = 0;

    /** Bounded initialization retries before the backend gives up and fails open. */
    public static final int INIT_MAX_ATTEMPTS = 10;
    public static final long INIT_RETRY_BACKOFF_MS = 5000L;

    /**
     * Only logical display 0 receives a screen range. Samsung swaps the physical panel
     * behind display 0 across fold states, while every other display (cover secondary,
     * DeX, virtual, external) must keep its original parameters untouched.
     */
    public static boolean appliesToDisplay(int displayId) {
        return displayId == MAIN_LOGICAL_DISPLAY_ID;
    }

    /**
     * Cheap identity for the panel behind a logical display. Samsung reuses the same
     * DisplayInfo object across a panel swap (fields are updated in place), so the
     * cached mode table must also compare logical dimensions and the active mode id.
     */
    public static int fingerprint(int logicalWidth, int logicalHeight, int modeId) {
        int result = logicalWidth;
        result = 31 * result + logicalHeight;
        result = 31 * result + modeId;
        return result;
    }

    /**
     * True when the cached mode table can no longer describe the panel: either the
     * DisplayInfo object was replaced or the in-place fingerprint moved. This is what
     * keeps a stale inner-panel table from being reused for the cover panel behind the
     * same logical display.
     */
    public static boolean modeTableStale(
            Object cachedInfo, int cachedFingerprint, Object currentInfo, int currentFingerprint) {
        return cachedInfo != currentInfo || cachedFingerprint != currentFingerprint;
    }

    /**
     * Intersects a configured screen range with the rates the panel actually exposes.
     * Returns the configured bounds untouched when at least one supported rate falls
     * inside, so the display-properties clamp and the preferred-mode clamp always act
     * on the same feasible range, or {@code null} when nothing supported fits (full
     * fail-open instead of applying only half of the feature).
     */
    public static float[] intersectWithSupportedRates(float min, float max, float[] rates) {
        if (rates == null) return null;
        for (float rate : rates) {
            if (withinRange(rate, min, max)) {
                return new float[]{min, max};
            }
        }
        return null;
    }

    /**
     * Shared editor validation: parses both fields as one pair, keeps the documented
     * 1..240 input bound, requires min <= max, and refuses ranges no supported mode can
     * satisfy. Returns {@code null} when the pair must not be saved.
     */
    public static float[] parseAndValidateRange(String minRaw, String maxRaw, float[] rates) {
        Float min = parseRate(minRaw);
        Float max = parseRate(maxRaw);
        if (min == null || max == null || !isValidScreenRange(min, max)) {
            return null;
        }
        return intersectWithSupportedRates(min, max, rates);
    }

    /**
     * Backoff gate for off-thread initialization retries: attempts are bounded and the
     * hot path never schedules more than one attempt per backoff window.
     */
    public static boolean initRetryAllowed(int completedAttempts, long now, long nextAttemptAt) {
        return completedAttempts < INIT_MAX_ATTEMPTS && now >= nextAttemptAt;
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
