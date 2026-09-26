package io.github.pigerzhu.onelab.feature.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.Map;

import io.github.pigerzhu.onelab.contract.RefreshRateOverride;
import io.github.pigerzhu.onelab.contract.RefreshRateOverrides;

public final class RefreshRateScreenPolicyTest {
    @Test
    public void editableOverridesSupportsAddingReplacingAndRemovingPolicies() {
        Map<String, RefreshRateOverride> overrides = RefreshRateScreen.editableOverrides(
                "com.existing:1:0.00:0.00");

        overrides.put("com.new", new RefreshRateOverride(
                RefreshRateOverrides.MODE_FIXED, 60f, 60f));
        overrides.put("com.existing", new RefreshRateOverride(
                RefreshRateOverrides.MODE_RANGE, 24f, 120f));
        overrides.remove("com.new");

        assertEquals(1, overrides.size());
        assertEquals(RefreshRateOverrides.MODE_RANGE, overrides.get("com.existing").mode);
        assertFalse(overrides.containsKey("com.new"));
    }
}
