package io.github.pigerzhu.onelab.hook.system;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Covers the pure merge rules between the per-app policy and the screen range. */
public final class RefreshRateScreenRangePolicyTest {
    private static final float[] INNER = {48f, 120f};
    private static final float[] OUTER = {24f, 60f};

    @Test
    public void disabledScreenConfigParsesToNull() {
        assertNull(RefreshRateScreenRangePolicy.parseScreenRange("0", "48", "120"));
        assertNull(RefreshRateScreenRangePolicy.parseScreenRange(null, "48", "120"));
        assertNull(RefreshRateScreenRangePolicy.parseScreenRange("1", "", "120"));
        assertNull(RefreshRateScreenRangePolicy.parseScreenRange("1", "abc", "120"));
        assertNull(RefreshRateScreenRangePolicy.parseScreenRange("1", "0", "120"));
        assertNull(RefreshRateScreenRangePolicy.parseScreenRange("1", "-5", "120"));
        assertNull(RefreshRateScreenRangePolicy.parseScreenRange("1", "241", "241"));
        assertNull(RefreshRateScreenRangePolicy.parseScreenRange("1", "120", "48"));
        assertNull(RefreshRateScreenRangePolicy.parseScreenRange("1", "1", "240.5"));
    }

    @Test
    public void validScreenConfigParsesToBounds() {
        assertArrayEquals(new float[]{48f, 120f},
                RefreshRateScreenRangePolicy.parseScreenRange("1", "48", "120"), 0f);
        assertArrayEquals(new float[]{60f, 60f},
                RefreshRateScreenRangePolicy.parseScreenRange("1", "60.0", "60"), 0f);
    }

    @Test
    public void onlyOneScreenNeedsToBeEnabled() {
        // Inner enabled alone; outer key still off.
        assertNull(RefreshRateScreenRangePolicy.parseScreenRange("0", "24", "60"));
        assertArrayEquals(INNER,
                RefreshRateScreenRangePolicy.parseScreenRange("1", "48", "120"), 0f);
    }

    @Test
    public void unknownPanelKeepsSystemBehavior() {
        assertNull(RefreshRateScreenRangePolicy.rangeForPanel(
                RefreshRateScreenRangePolicy.PANEL_UNKNOWN, INNER, OUTER));
        assertNull(RefreshRateScreenRangePolicy.rangeForPanel(
                RefreshRateScreenRangePolicy.PANEL_UNKNOWN, INNER, null));
    }

    @Test
    public void panelNameMappingCoversFoldStates() {
        assertEquals(RefreshRateScreenRangePolicy.PANEL_OUTER,
                RefreshRateScreenRangePolicy.panelForStateName("CLOSED"));
        assertEquals(RefreshRateScreenRangePolicy.PANEL_OUTER,
                RefreshRateScreenRangePolicy.panelForStateName("CLOSE"));
        assertEquals(RefreshRateScreenRangePolicy.PANEL_OUTER,
                RefreshRateScreenRangePolicy.panelForStateName("TENT"));
        assertEquals(RefreshRateScreenRangePolicy.PANEL_INNER,
                RefreshRateScreenRangePolicy.panelForStateName("OPENED"));
        assertEquals(RefreshRateScreenRangePolicy.PANEL_INNER,
                RefreshRateScreenRangePolicy.panelForStateName("OPEN"));
        assertEquals(RefreshRateScreenRangePolicy.PANEL_INNER,
                RefreshRateScreenRangePolicy.panelForStateName("HALF_OPENED"));
        assertEquals(RefreshRateScreenRangePolicy.PANEL_INNER,
                RefreshRateScreenRangePolicy.panelForStateName("HALF_FOLDED"));
        assertEquals(RefreshRateScreenRangePolicy.PANEL_INNER,
                RefreshRateScreenRangePolicy.panelForStateName("CONCURRENT_INNER_DEFAULT"));
        assertEquals(RefreshRateScreenRangePolicy.PANEL_OUTER,
                RefreshRateScreenRangePolicy.panelForStateName("CONCURRENT_OUTER_DEFAULT"));
        assertEquals(RefreshRateScreenRangePolicy.PANEL_UNKNOWN,
                RefreshRateScreenRangePolicy.panelForStateName(null));
        assertEquals(RefreshRateScreenRangePolicy.PANEL_UNKNOWN,
                RefreshRateScreenRangePolicy.panelForStateName(""));
        assertEquals(RefreshRateScreenRangePolicy.PANEL_UNKNOWN,
                RefreshRateScreenRangePolicy.panelForStateName("MYSTERY_STATE"));
    }

    @Test
    public void foldSwitchSelectsTheCachedRangeOfTheNewPanel() {
        assertEquals(INNER, RefreshRateScreenRangePolicy.rangeForPanel(
                RefreshRateScreenRangePolicy.PANEL_INNER, INNER, OUTER));
        assertEquals(OUTER, RefreshRateScreenRangePolicy.rangeForPanel(
                RefreshRateScreenRangePolicy.PANEL_OUTER, INNER, OUTER));
        // A disabled panel side stays disabled even when the other side is configured.
        assertNull(RefreshRateScreenRangePolicy.rangeForPanel(
                RefreshRateScreenRangePolicy.PANEL_OUTER, INNER, null));
        assertNull(RefreshRateScreenRangePolicy.rangeForPanel(
                RefreshRateScreenRangePolicy.PANEL_INNER, null, OUTER));
    }

    @Test
    public void adaptivePolicyStaysDynamicInsideTheScreenRange() {
        // An adaptive app votes no min/max; the screen range becomes the whole vote.
        assertArrayEquals(new float[]{48f, 120f},
                RefreshRateScreenRangePolicy.mergeDisplayRequest(0f, 0f, 48f, 120f), 0f);
    }

    @Test
    public void fixedRateInsideTheScreenRangeIsKept() {
        assertArrayEquals(new float[]{24f, 60f},
                RefreshRateScreenRangePolicy.mergeDisplayRequest(0f, 60f, 24f, 90f), 0f);
        assertArrayEquals(new float[]{30f, 30f},
                RefreshRateScreenRangePolicy.mergeDisplayRequest(30f, 30f, 24f, 90f), 0f);
    }

    @Test
    public void fixedRateAboveTheScreenMaximumClampsToTheMaximum() {
        assertArrayEquals(new float[]{48f, 60f},
                RefreshRateScreenRangePolicy.mergeDisplayRequest(0f, 120f, 48f, 60f), 0f);
        assertArrayEquals(new float[]{60f, 60f},
                RefreshRateScreenRangePolicy.mergeDisplayRequest(90f, 120f, 48f, 60f), 0f);
    }

    @Test
    public void fixedRateBelowTheScreenMinimumClampsToTheMinimum() {
        assertArrayEquals(new float[]{48f, 48f},
                RefreshRateScreenRangePolicy.mergeDisplayRequest(0f, 24f, 48f, 60f), 0f);
        assertArrayEquals(new float[]{48f, 48f},
                RefreshRateScreenRangePolicy.mergeDisplayRequest(24f, 30f, 48f, 60f), 0f);
    }

    @Test
    public void overlappingAppRangeUsesTheIntersection() {
        assertArrayEquals(new float[]{48f, 60f},
                RefreshRateScreenRangePolicy.merge(30f, 90f, 48f, 60f), 0f);
        assertArrayEquals(new float[]{50f, 55f},
                RefreshRateScreenRangePolicy.merge(50f, 55f, 48f, 60f), 0f);
        assertArrayEquals(new float[]{48f, 55f},
                RefreshRateScreenRangePolicy.merge(30f, 55f, 48f, 60f), 0f);
    }

    @Test
    public void appRangeAboveTheScreenRangeCollapsesToTheMaximum() {
        assertArrayEquals(new float[]{60f, 60f},
                RefreshRateScreenRangePolicy.merge(90f, 120f, 48f, 60f), 0f);
    }

    @Test
    public void appRangeBelowTheScreenRangeCollapsesToTheMinimum() {
        assertArrayEquals(new float[]{48f, 48f},
                RefreshRateScreenRangePolicy.merge(24f, 30f, 48f, 60f), 0f);
    }

    @Test
    public void identicalAppAndScreenRangeIsUnchanged() {
        assertArrayEquals(new float[]{48f, 120f},
                RefreshRateScreenRangePolicy.merge(48f, 120f, 48f, 120f), 0f);
    }

    @Test
    public void singleRateClampOnlyTouchesPositiveRates() {
        assertEquals(0f, RefreshRateScreenRangePolicy.clampRequest(0f, 48f, 60f), 0f);
        assertEquals(55f, RefreshRateScreenRangePolicy.clampRequest(55f, 48f, 60f), 0f);
        assertEquals(60f, RefreshRateScreenRangePolicy.clampRequest(120f, 48f, 60f), 0f);
        assertEquals(48f, RefreshRateScreenRangePolicy.clampRequest(24f, 48f, 60f), 0f);
    }

    @Test
    public void modeSelectionKeepsInRangeModes() {
        int[] ids = {1, 2, 3, 4};
        float[] rates = {24f, 48f, 60f, 120f};
        assertEquals(4, RefreshRateScreenRangePolicy.pickModeId(
                ids, rates, 120f, 48f, 120f, 0));
        assertEquals(2, RefreshRateScreenRangePolicy.pickModeId(
                ids, rates, 48f, 24f, 90f, 0));
    }

    @Test
    public void modeSelectionRemapsOutOfRangeFixedModes() {
        int[] ids = {1, 2, 3, 4};
        float[] rates = {24f, 48f, 60f, 120f};
        assertEquals(3, RefreshRateScreenRangePolicy.pickModeId(
                ids, rates, 120f, 48f, 60f, 0));
        assertEquals(2, RefreshRateScreenRangePolicy.pickModeId(
                ids, rates, 24f, 48f, 60f, 0));
    }

    @Test
    public void modeSelectionToleratesSamsung119Dot99Modes() {
        int[] ids = {1, 2};
        float[] rates = {48f, 119.99f};
        assertEquals(2, RefreshRateScreenRangePolicy.pickModeId(
                ids, rates, 119.99f, 48f, 120f, 0));
    }

    @Test
    public void modeSelectionFailsOpenWithoutCandidates() {
        int[] ids = {1, 2, 3};
        float[] rates = {24f, 48f, 60f};
        assertEquals(-1, RefreshRateScreenRangePolicy.pickModeId(
                ids, rates, 60f, 100f, 110f, 0));
        assertEquals(-1, RefreshRateScreenRangePolicy.pickModeId(
                null, null, 60f, 48f, 60f, 0));
    }

    @Test
    public void rangeCheckUsesTolerance() {
        assertTrue(RefreshRateScreenRangePolicy.withinRange(119.99f, 48f, 120f));
        assertTrue(RefreshRateScreenRangePolicy.withinRange(48.25f, 48f, 60f));
        assertFalse(RefreshRateScreenRangePolicy.withinRange(121f, 48f, 120f));
        assertFalse(RefreshRateScreenRangePolicy.withinRange(47f, 48f, 60f));
    }

    @Test
    public void screenRangeValidationMatchesTheDocumentedBounds() {
        assertTrue(RefreshRateScreenRangePolicy.isValidScreenRange(1f, 240f));
        assertTrue(RefreshRateScreenRangePolicy.isValidScreenRange(60f, 60f));
        assertFalse(RefreshRateScreenRangePolicy.isValidScreenRange(0f, 120f));
        assertFalse(RefreshRateScreenRangePolicy.isValidScreenRange(120f, 48f));
        assertFalse(RefreshRateScreenRangePolicy.isValidScreenRange(Float.NaN, 120f));
        assertFalse(RefreshRateScreenRangePolicy.isValidScreenRange(48f, Float.POSITIVE_INFINITY));
    }

    @Test
    public void onlyLogicalDisplayZeroReceivesAScreenRange() {
        assertTrue(RefreshRateScreenRangePolicy.appliesToDisplay(0));
        assertFalse(RefreshRateScreenRangePolicy.appliesToDisplay(1));
        assertFalse(RefreshRateScreenRangePolicy.appliesToDisplay(2));
        assertFalse(RefreshRateScreenRangePolicy.appliesToDisplay(-1));
        assertFalse(RefreshRateScreenRangePolicy.appliesToDisplay(4096));
    }

    @Test
    public void modeTableStaysFreshForAnUnchangedPanel() {
        Object displayInfo = new Object();
        int fingerprint = RefreshRateScreenRangePolicy.fingerprint(1856, 2160, 1);
        assertFalse(RefreshRateScreenRangePolicy.modeTableStale(
                displayInfo, fingerprint, displayInfo, fingerprint));
    }

    @Test
    public void modeTableIsInvalidatedWhenThePanelBehindTheSameLogicalDisplayChanges() {
        // Fold swap: Samsung mutates logical display 0's DisplayInfo in place, so the
        // object identity is unchanged while the panel (and its mode ids) change.
        Object displayInfo = new Object();
        int innerFingerprint = RefreshRateScreenRangePolicy.fingerprint(1856, 2160, 1);
        int outerFingerprint = RefreshRateScreenRangePolicy.fingerprint(968, 2376, 8);
        assertTrue(RefreshRateScreenRangePolicy.modeTableStale(
                displayInfo, innerFingerprint, displayInfo, outerFingerprint));
        // A replaced DisplayInfo object must also invalidate the cache.
        assertTrue(RefreshRateScreenRangePolicy.modeTableStale(
                displayInfo, innerFingerprint, new Object(), innerFingerprint));
    }

    @Test
    public void fingerprintDistinguishesTheTwoFoldPanels() {
        int inner = RefreshRateScreenRangePolicy.fingerprint(1856, 2160, 1);
        int cover = RefreshRateScreenRangePolicy.fingerprint(968, 2376, 8);
        assertTrue(inner != cover);
    }

    @Test
    public void rangeIntersectsSupportedRatesKeepsTheConfiguredBounds() {
        float[] rates = {120.00001f, 96.00001f, 60.000004f, 48.000004f, 30.000002f, 24.000002f, 10f};
        assertArrayEquals(new float[]{48f, 60f},
                RefreshRateScreenRangePolicy.intersectWithSupportedRates(48f, 60f, rates), 0f);
        assertArrayEquals(new float[]{10f, 120f},
                RefreshRateScreenRangePolicy.intersectWithSupportedRates(10f, 120f, rates), 0f);
        // 45-65 keeps the configured bounds because 48 and 60 fall inside it.
        assertArrayEquals(new float[]{45f, 65f},
                RefreshRateScreenRangePolicy.intersectWithSupportedRates(45f, 65f, rates), 0f);
    }

    @Test
    public void rangeWithoutAnySupportedModeFailsOpenCompletely() {
        float[] rates = {120.00001f, 96.00001f, 60.000004f, 48.000004f, 30.000002f, 24.000002f, 10f};
        // 61-89 holds no mode of this panel: both the display-properties clamp and the
        // preferred-mode clamp must refuse instead of applying only half the feature.
        assertNull(RefreshRateScreenRangePolicy.intersectWithSupportedRates(61f, 89f, rates));
        assertNull(RefreshRateScreenRangePolicy.intersectWithSupportedRates(48f, 60f, null));
        // Samsung exposes 119.99 Hz style modes; the tolerance must accept them.
        assertNotNull(RefreshRateScreenRangePolicy.intersectWithSupportedRates(
                48f, 120f, new float[]{48f, 119.99f}));
    }

    @Test
    public void editorValidationRequiresAFeasiblePair() {
        float[] rates = {120.00001f, 60.000004f, 48.000004f, 10f};
        assertArrayEquals(new float[]{48f, 120f},
                RefreshRateScreenRangePolicy.parseAndValidateRange("48", "120", rates), 0f);
        // Same pair must be rejected as a whole when no mode fits, never half-applied.
        assertNull(RefreshRateScreenRangePolicy.parseAndValidateRange("90", "100", rates));
        assertNull(RefreshRateScreenRangePolicy.parseAndValidateRange("120", "48", rates));
        assertNull(RefreshRateScreenRangePolicy.parseAndValidateRange("0", "120", rates));
        assertNull(RefreshRateScreenRangePolicy.parseAndValidateRange("abc", "120", rates));
        assertNull(RefreshRateScreenRangePolicy.parseAndValidateRange(null, "120", rates));
    }

    @Test
    public void initializationRetryIsBoundedAndBackedOff() {
        long now = 1000L;
        assertTrue(RefreshRateScreenRangePolicy.initRetryAllowed(0, now, 0L));
        assertTrue(RefreshRateScreenRangePolicy.initRetryAllowed(9, now, 999L));
        // Backoff window not elapsed yet.
        assertFalse(RefreshRateScreenRangePolicy.initRetryAllowed(3, now, 2000L));
        // Budget exhausted: permanent fail-open instead of endless hot-path retries.
        assertFalse(RefreshRateScreenRangePolicy.initRetryAllowed(
                RefreshRateScreenRangePolicy.INIT_MAX_ATTEMPTS, now, 0L));
        assertFalse(RefreshRateScreenRangePolicy.initRetryAllowed(
                RefreshRateScreenRangePolicy.INIT_MAX_ATTEMPTS + 5, now, 0L));
    }
}
