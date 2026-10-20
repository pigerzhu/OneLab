package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.List;

public class TikTokLargeScreenTargetsTest {
    @Test
    public void acceptsOneSemanticTarget() {
        assertEquals("target", TikTokLargeScreenTargets.requireUnique(
                "ug_pad_comments_side_panel_enabled", List.of("target")));
    }

    @Test
    public void rejectsMissingOrAmbiguousSemanticTargets() {
        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> TikTokLargeScreenTargets.requireUnique(
                        "ug_pad_comments_side_panel_enabled", List.of()));
        assertEquals(
                "Expected one TikTok target for ug_pad_comments_side_panel_enabled, found 0",
                missing.getMessage());

        assertThrows(IllegalStateException.class,
                () -> TikTokLargeScreenTargets.requireUnique(
                        "ug_pad_comments_side_panel_enabled",
                        List.of("first", "second")));
    }
}
