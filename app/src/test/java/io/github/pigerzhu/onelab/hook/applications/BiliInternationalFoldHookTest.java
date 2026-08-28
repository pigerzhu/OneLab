package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class BiliInternationalFoldHookTest {
    @Test
    public void rewritesOnlyDisabledLargeScreenGate() {
        assertEquals(
                "large",
                BiliInternationalPolicy.rewriteConfigValue(
                        "dd_screen_adjust_xiaomi_864", "off", true));
        assertEquals(
                "medium",
                BiliInternationalPolicy.rewriteConfigValue(
                        "dd_screen_adjust_xiaomi_864", "medium", true));
        assertEquals(
                "off",
                BiliInternationalPolicy.rewriteConfigValue(
                        "another_key", "off", true));
        assertEquals(
                "off",
                BiliInternationalPolicy.rewriteConfigValue(
                        "dd_screen_adjust_xiaomi_864", "off", false));
    }
}
