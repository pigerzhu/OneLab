package io.github.pigerzhu.onelab.hook.applications;

final class XhsImageFullscreenPolicy {
    static final String TARGET_ACTIVITY_SUFFIX = ".NoteDetailActivity";
    static final long VIEWER_SETTLE_DELAY_MS = 500L;
    private static final float MIN_VIEWER_HEIGHT_RATIO = 0.9f;

    private XhsImageFullscreenPolicy() {
    }

    static boolean isEnabled(String master, String app) {
        return "1".equals(master) && "1".equals(app);
    }

    static boolean isViewerCandidate(
            String activityClassName,
            boolean recyclerView,
            boolean hasPhotoLayout,
            boolean hasMediaContainer,
            int rootHeight,
            int candidateHeight) {
        return activityClassName != null
                && activityClassName.endsWith(TARGET_ACTIVITY_SUFFIX)
                && recyclerView
                && hasPhotoLayout
                && hasMediaContainer
                && rootHeight > 0
                && candidateHeight >= Math.round(rootHeight * MIN_VIEWER_HEIGHT_RATIO);
    }
}
