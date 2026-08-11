package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TikTokLargeScreenPolicyTest {
    @Test
    public void forcesOnlyCommentSidePanelBusinessKeys() {
        assertTrue(TikTokLargeScreenPolicy.shouldForceCommentGate(
                true, "ug_pad_comments_side_panel_enabled"));
        assertTrue(TikTokLargeScreenPolicy.shouldForceCommentGate(
                true, "comment_split_ab_override_foldable"));
        assertFalse(TikTokLargeScreenPolicy.shouldForceCommentGate(
                true, "ug_tablet_search_result_redesign_style"));
        assertFalse(TikTokLargeScreenPolicy.shouldForceCommentGate(
                true, "LivePadMultiScreenSetting"));
    }

    @Test
    public void preservesAllTikTokBehaviorWhenDisabled() {
        assertFalse(TikTokLargeScreenPolicy.shouldForceCommentGate(
                false, "ug_pad_comments_side_panel_enabled"));
        assertFalse(TikTokLargeScreenPolicy.shouldForceCommentGate(
                false, "comment_split_ab_override_foldable"));
    }

    @Test
    public void forcesLiveMultiScreenOnlyWhenEnabled() {
        assertTrue(TikTokLargeScreenPolicy.shouldForceLiveMultiScreen(true));
        assertFalse(TikTokLargeScreenPolicy.shouldForceLiveMultiScreen(false));
    }

    @Test
    public void forcesPortraitCommentGateOnlyForUnfoldedFullScreenWindow() {
        assertTrue(TikTokLargeScreenPolicy.shouldForcePortraitComments(
                true, true, 707, 823, false, false));
        assertFalse(TikTokLargeScreenPolicy.shouldForcePortraitComments(
                false, true, 707, 823, false, false));
        assertFalse(TikTokLargeScreenPolicy.shouldForcePortraitComments(
                true, false, 707, 823, false, false));
        assertFalse(TikTokLargeScreenPolicy.shouldForcePortraitComments(
                true, true, 424, 787, false, false));
        assertFalse(TikTokLargeScreenPolicy.shouldForcePortraitComments(
                true, true, 823, 707, false, false));
        assertFalse(TikTokLargeScreenPolicy.shouldForcePortraitComments(
                true, true, 707, 823, true, false));
        assertFalse(TikTokLargeScreenPolicy.shouldForcePortraitComments(
                true, true, 707, 823, false, true));
    }

    @Test
    public void halvesPortraitPanelWidthAndPreservesOtherWindows() {
        assertEquals(928, TikTokLargeScreenPolicy.resolveCommentPanelWidthPx(
                true, true, 707, 823, 1856, 1016));
        assertEquals(1016, TikTokLargeScreenPolicy.resolveCommentPanelWidthPx(
                true, true, 823, 707, 2160, 1016));
        assertEquals(1016, TikTokLargeScreenPolicy.resolveCommentPanelWidthPx(
                true, true, 424, 787, 1113, 1016));
        assertEquals(1016, TikTokLargeScreenPolicy.resolveCommentPanelWidthPx(
                false, true, 707, 823, 1856, 1016));
        assertEquals(1016, TikTokLargeScreenPolicy.resolveCommentPanelWidthPx(
                true, false, 707, 823, 1856, 1016));
    }

    @Test
    public void acceptsOnlyTikTokMainProcess() {
        assertTrue(TikTokLargeScreenPolicy.isMainProcess(
                "com.zhiliaoapp.musically", "com.zhiliaoapp.musically"));
        assertFalse(TikTokLargeScreenPolicy.isMainProcess(
                "com.zhiliaoapp.musically", "com.zhiliaoapp.musically:push"));
        assertFalse(TikTokLargeScreenPolicy.isMainProcess(
                "com.ss.android.ugc.trill", "com.ss.android.ugc.trill"));
    }
}
