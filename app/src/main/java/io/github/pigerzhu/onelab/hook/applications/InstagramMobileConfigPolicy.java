package io.github.pigerzhu.onelab.hook.applications;

import io.github.pigerzhu.onelab.hook.core.HookConstants;

import java.util.List;

final class InstagramMobileConfigPolicy {
    static final long ADAPTIVE_LARGE_SCREEN_GATE = 36325123995030568L;
    static final long REELS_TWO_PANE_COMMENTS_GATE = 36325123993916442L;

    static boolean shouldForce(boolean enabled, long key) {
        return enabled && (key == ADAPTIVE_LARGE_SCREEN_GATE
                || key == REELS_TWO_PANE_COMMENTS_GATE);
    }

    static List<Long> gateKeys() {
        return List.of(ADAPTIVE_LARGE_SCREEN_GATE, REELS_TWO_PANE_COMMENTS_GATE);
    }

    static boolean isMainProcess(String packageName, String processName) {
        return HookConstants.INSTAGRAM_PACKAGE.equals(packageName)
                && packageName.equals(processName);
    }

    private InstagramMobileConfigPolicy() {
    }
}
