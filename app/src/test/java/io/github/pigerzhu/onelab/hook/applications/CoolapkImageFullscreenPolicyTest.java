package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CoolapkImageFullscreenPolicyTest {

    @Test
    public void requiresBothMasterAndCoolapkSwitches() {
        assertTrue(CoolapkImageFullscreenPolicy.isEnabled("1", "1"));
        assertFalse(CoolapkImageFullscreenPolicy.isEnabled("0", "1"));
        assertFalse(CoolapkImageFullscreenPolicy.isEnabled("1", "0"));
        assertFalse(CoolapkImageFullscreenPolicy.isEnabled(null, "1"));
    }

    @Test
    public void targetsOnlyCoolapksFullscreenActivity() {
        assertEquals("com.coolapk.market",
                CoolapkImageFullscreenPolicy.TARGET_PACKAGE);
        assertEquals("com.coolapk.market.view.photo.PhotoViewV16Activity",
                CoolapkImageFullscreenPolicy.TARGET_ACTIVITY);
    }
}
