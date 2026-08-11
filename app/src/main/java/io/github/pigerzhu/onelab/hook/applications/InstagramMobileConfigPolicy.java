package io.github.pigerzhu.onelab.hook.applications;

final class InstagramMobileConfigPolicy {
    static final long ADAPTIVE_LARGE_SCREEN_GATE = 36325123995030568L;
    static final long REELS_TWO_PANE_COMMENTS_GATE = 36325123993916442L;

    static boolean shouldForce(long key) {
        return key == ADAPTIVE_LARGE_SCREEN_GATE
                || key == REELS_TWO_PANE_COMMENTS_GATE;
    }

    private InstagramMobileConfigPolicy() {
    }
}
