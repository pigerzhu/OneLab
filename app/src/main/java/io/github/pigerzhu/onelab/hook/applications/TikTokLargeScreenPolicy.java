package io.github.pigerzhu.onelab.hook.applications;

import io.github.pigerzhu.onelab.hook.core.HookConstants;

final class TikTokLargeScreenPolicy {
    static final String COMMENTS_GATE = "ug_pad_comments_side_panel_enabled";
    static final String FOLDABLE_OVERRIDE = "comment_split_ab_override_foldable";
    static final String PORTRAIT_COMMENT_GATE = "isOptCommentSplit";
    static final String COMMENT_PANEL_WIDTH = "comment_panel_width";
    static final String SEARCH_GATE = "ug_tablet_search_result_redesign_style";
    static final String LIVE_MULTI_SCREEN_CLASS =
            "com.bytedance.android.livesdk.livesetting.message.LivePadMultiScreenSetting";

    static boolean shouldForceCommentGate(boolean enabled, String key) {
        return enabled && (COMMENTS_GATE.equals(key) || FOLDABLE_OVERRIDE.equals(key));
    }

    static boolean shouldForceLiveMultiScreen(boolean enabled) {
        return enabled;
    }

    static boolean shouldForcePortraitComments(boolean commentsEnabled, boolean portraitEnabled,
            int widthDp, int heightDp, boolean inMultiWindow, boolean inPictureInPicture) {
        return commentsEnabled && portraitEnabled && widthDp >= 600 && heightDp >= 683
                && widthDp < heightDp
                && !inMultiWindow && !inPictureInPicture;
    }

    static int resolveCommentPanelWidthPx(boolean commentsEnabled, boolean portraitEnabled,
            int widthDp, int heightDp, int widthPx, int originalWidthPx) {
        if (!commentsEnabled || !portraitEnabled || widthDp < 600 || heightDp < 683
                || widthDp >= heightDp || widthPx <= 0) {
            return originalWidthPx;
        }
        return widthPx / 2;
    }

    static boolean isMainProcess(String packageName, String processName) {
        return HookConstants.TIKTOK_PACKAGE.equals(packageName)
                && packageName.equals(processName);
    }

    private TikTokLargeScreenPolicy() {
    }
}
