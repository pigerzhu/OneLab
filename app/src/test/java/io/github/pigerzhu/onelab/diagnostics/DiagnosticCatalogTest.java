package io.github.pigerzhu.onelab.diagnostics;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import io.github.pigerzhu.onelab.contract.SettingsKeys;

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

    @Test
    public void catalogIncludesSplitImageFullscreenSwitches() {
        assertTrue(hasFeature("experiments.split_image_fullscreen"));
        assertTrue(hasFeature("apps.coolapk_image_fullscreen"));
        assertTrue(hasFeature("apps.xhs_image_fullscreen"));
    }

    @Test
    public void everySettingsKeyHasADiagnosticOwner() throws IllegalAccessException {
        Set<String> covered = new HashSet<>();
        for (DiagnosticCatalog.Feature feature : DiagnosticCatalog.FEATURES) {
            covered.add(feature.settingKey);
        }
        for (DiagnosticCatalog.Value value : DiagnosticCatalog.VALUES) {
            covered.add(value.settingKey);
        }
        covered.add(SettingsKeys.KEY_SPLIT_VIEW_ALLOWED_PACKAGES);

        for (Field field : SettingsKeys.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    || field.getType() != String.class
                    || !field.getName().startsWith("KEY_")) {
                continue;
            }
            String key = (String) field.get(null);
            assertTrue(field.getName() + " is missing from diagnostics", covered.contains(key));
        }
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
