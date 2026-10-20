package io.github.pigerzhu.onelab.contract;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/** Locks the historical onelab_refresh_rate_overrides format used by the per-app policy. */
public final class RefreshRateOverridesTest {
    @Test
    public void parseAcceptsTheDocumentedModes() {
        Map<String, RefreshRateOverride> parsed = RefreshRateOverrides.parse(
                "com.app.one:1:0:0;com.app.two:2:60.00:60.00;"
                        + "com.app.three:2:-1.00:-1.00;com.app.four:3:40.00:60.00");
        assertEquals(4, parsed.size());
        assertEquals(RefreshRateOverrides.MODE_HIGH_REFRESH_BYPASS,
                parsed.get("com.app.one").mode);
        assertEquals(RefreshRateOverrides.MODE_FIXED, parsed.get("com.app.two").mode);
        assertEquals(60f, parsed.get("com.app.two").min, 0f);
        assertEquals(RefreshRateOverrides.MODE_FIXED, parsed.get("com.app.three").mode);
        assertEquals(-1f, parsed.get("com.app.three").min, 0f);
        assertEquals(RefreshRateOverrides.MODE_RANGE, parsed.get("com.app.four").mode);
        assertEquals(40f, parsed.get("com.app.four").min, 0f);
        assertEquals(60f, parsed.get("com.app.four").max, 0f);
    }

    @Test
    public void parseSkipsMalformedEntriesWithoutLosingTheRest() {
        Map<String, RefreshRateOverride> parsed = RefreshRateOverrides.parse(
                "com.app.good:1:0:0;not-an-entry;:1:0:0;com.app.bad:2:x;com.app.bad:3:60:40;"
                        + "com.app.good2:9:1:1");
        assertEquals(1, parsed.size());
        assertTrue(parsed.containsKey("com.app.good"));
    }

    @Test
    public void parseHandlesLegacyTwoFieldEntries() {
        Map<String, RefreshRateOverride> parsed = RefreshRateOverrides.parse("com.app.one:1");
        assertEquals(1, parsed.size());
        assertEquals(RefreshRateOverrides.MODE_HIGH_REFRESH_BYPASS,
                parsed.get("com.app.one").mode);
        assertEquals(0f, parsed.get("com.app.one").min, 0f);
        assertEquals(0f, parsed.get("com.app.one").max, 0f);
    }

    @Test
    public void parseReturnsEmptyMapForNullOrBlankInput() {
        assertTrue(RefreshRateOverrides.parse(null).isEmpty());
        assertTrue(RefreshRateOverrides.parse("").isEmpty());
        assertTrue(RefreshRateOverrides.parse("   ").isEmpty());
    }

    @Test
    public void parseRejectsInvalidFixedAndRangeValues() {
        assertTrue(RefreshRateOverrides.parse("com.app.a:2:0:0").isEmpty());
        assertTrue(RefreshRateOverrides.parse("com.app.b:2:-2:0").isEmpty());
        assertTrue(RefreshRateOverrides.parse("com.app.c:3:0:60").isEmpty());
        assertTrue(RefreshRateOverrides.parse("com.app.d:3:60:40").isEmpty());
    }

    @Test
    public void serializeMatchesTheHistoricalSaveFormat() {
        Map<String, RefreshRateOverride> values = new LinkedHashMap<>();
        values.put("com.app.one", new RefreshRateOverride(
                RefreshRateOverrides.MODE_HIGH_REFRESH_BYPASS, 0f, 0f));
        values.put("com.app.two", new RefreshRateOverride(
                RefreshRateOverrides.MODE_FIXED, 60f, 60f));
        values.put("com.app.three", new RefreshRateOverride(
                RefreshRateOverrides.MODE_RANGE, 40f, 60f));
        assertEquals("com.app.one:1:0.00:0.00;com.app.two:2:60.00:60.00"
                        + ";com.app.three:3:40.00:60.00",
                RefreshRateOverrides.serialize(values));
    }

    @Test
    public void serializeThenParseRoundTrips() {
        Map<String, RefreshRateOverride> values = new LinkedHashMap<>();
        values.put("com.app.two", new RefreshRateOverride(
                RefreshRateOverrides.MODE_FIXED, 90.5f, 90.5f));
        values.put("com.app.four", new RefreshRateOverride(
                RefreshRateOverrides.MODE_RANGE, 40.25f, 60.75f));
        Map<String, RefreshRateOverride> parsed = RefreshRateOverrides.parse(
                RefreshRateOverrides.serialize(values));
        assertEquals(2, parsed.size());
        assertEquals(90.5f, parsed.get("com.app.two").min, 0.001f);
        assertEquals(40.25f, parsed.get("com.app.four").min, 0.001f);
        assertEquals(60.75f, parsed.get("com.app.four").max, 0.001f);
    }

    @Test
    public void parseResultIsImmutable() {
        Map<String, RefreshRateOverride> parsed = RefreshRateOverrides.parse("com.app.one:1");
        try {
            parsed.put("com.app.hack", new RefreshRateOverride(1, 0f, 0f));
            throw new AssertionError("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            assertNull(null);
        }
    }
}
