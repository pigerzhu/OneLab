package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class XhsImageFullscreenPolicyTest {
    @Test
    public void waitsForTheNativeEnterAnimationBeforeExpanding() {
        assertTrue(XhsImageFullscreenPolicy.VIEWER_SETTLE_DELAY_MS >= 400L);
    }

    @Test
    public void requiresMasterAndPerAppSwitches() {
        assertTrue(XhsImageFullscreenPolicy.isEnabled("1", "1"));
        assertFalse(XhsImageFullscreenPolicy.isEnabled("0", "1"));
        assertFalse(XhsImageFullscreenPolicy.isEnabled("1", "0"));
    }

    @Test
    public void acceptsFullHeightNativePhotoRecycler() {
        assertTrue(XhsImageFullscreenPolicy.isViewerCandidate(
                "com.xingin.matrix.notedetail.NoteDetailActivity",
                true, true, true, 2160, 2160));
    }

    @Test
    public void rejectsInlinePhotoListAndIncompleteStructures() {
        assertFalse(XhsImageFullscreenPolicy.isViewerCandidate(
                "com.xingin.matrix.notedetail.NoteDetailActivity",
                true, true, true, 2160, 1281));
        assertFalse(XhsImageFullscreenPolicy.isViewerCandidate(
                "com.xingin.matrix.notedetail.NoteDetailActivity",
                true, true, false, 2160, 2160));
        assertFalse(XhsImageFullscreenPolicy.isViewerCandidate(
                "com.xingin.OtherActivity", true, true, true, 2160, 2160));
    }
}
