package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertFalse;
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
    public void acceptsOnlyTikTokMainProcess() {
        assertTrue(TikTokLargeScreenPolicy.isMainProcess(
                "com.zhiliaoapp.musically", "com.zhiliaoapp.musically"));
        assertFalse(TikTokLargeScreenPolicy.isMainProcess(
                "com.zhiliaoapp.musically", "com.zhiliaoapp.musically:push"));
        assertFalse(TikTokLargeScreenPolicy.isMainProcess(
                "com.ss.android.ugc.trill", "com.ss.android.ugc.trill"));
    }
}
