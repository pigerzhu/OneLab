package io.github.pigerzhu.onelab.diagnostics;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DiagnosticCatalogTest {
    @Test
    public void catalogIncludesRecentlyAddedFeatureSwitches() {
        assertTrue(hasFeature("apps.bilibili_tablet_layout"));
        assertTrue(hasFeature("apps.ithome_embedding"));
    }

    @Test
    public void catalogIncludesDynamicGpuAndSplitSnapshots() {
        assertTrue(hasValue("experiments.gpu_supported_frequencies"));
        assertTrue(hasValue("display.split_view_ratio_overrides"));
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
