package io.github.pigerzhu.onelab.diagnostics;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DiagnosticCatalogTest {
    @Test
    public void catalogIncludesRecentlyAddedFeatureSwitches() {
        assertTrue(hasFeature("apps.bilibili_tablet_layout"));
        assertTrue(hasFeature("apps.bilibili_in_fold"));
        assertTrue(hasFeature("apps.bilibili_in_tablet_layout"));
        assertTrue(hasFeature("apps.ithome_embedding"));
    }

    @Test
    public void catalogIncludesDynamicGpuAndSplitSnapshots() {
        assertTrue(hasValue("experiments.gpu_supported_frequencies"));
        assertTrue(hasValue("display.split_view_ratio_overrides"));
    }

    @Test
    public void catalogIncludesScreenRefreshRateRangeDiagnostics() {
        assertTrue(hasFeature("window.refresh_rate_screen_inner"));
        assertTrue(hasFeature("window.refresh_rate_screen_outer"));
        assertTrue(hasValue("display.refresh_rate_screen_inner_min"));
        assertTrue(hasValue("display.refresh_rate_screen_inner_max"));
        assertTrue(hasValue("display.refresh_rate_screen_outer_min"));
        assertTrue(hasValue("display.refresh_rate_screen_outer_max"));
        assertTrue(hasValue("display.refresh_rate_screen_runtime_status"));
    }

    private static boolean hasFeature(String id) {
        for (DiagnosticCatalog.Feature feature : DiagnosticCatalog.FEATURES) {
            if (id.equals(feature.id)) return true;
        }
        return false;
    }

    private static boolean hasValue(String id) {
        for (DiagnosticCatalog.Value value : DiagnosticCatalog.VALUES) {
            if (id.equals(value.id)) return true;
        }
        return false;
    }
}
