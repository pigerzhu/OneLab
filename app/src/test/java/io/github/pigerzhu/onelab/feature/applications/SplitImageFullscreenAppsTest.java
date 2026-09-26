package io.github.pigerzhu.onelab.feature.applications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SplitImageFullscreenAppsTest {
    @Test
    public void mapsEachSupportedApplicationToItsOwnSetting() {
        assertEquals("onelab_coolapk_image_fullscreen",
                SplitImageFullscreenApps.settingKey("com.coolapk.market"));
        assertEquals("onelab_weibo_image_fullscreen",
                SplitImageFullscreenApps.settingKey("com.sina.weibo"));
    }

    @Test
    public void rejectsApplicationsWithoutFullscreenSupport() {
        assertTrue(SplitImageFullscreenApps.supports("com.sina.weibo"));
        assertFalse(SplitImageFullscreenApps.supports("com.xingin.xhs"));
        assertFalse(SplitImageFullscreenApps.supports("com.example.unsupported"));
        assertNull(SplitImageFullscreenApps.settingKey("com.example.unsupported"));
    }
}
