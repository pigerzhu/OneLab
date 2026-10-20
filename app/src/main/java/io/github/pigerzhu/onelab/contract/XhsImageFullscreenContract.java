package io.github.pigerzhu.onelab.contract;

/** Runtime signal shared by the XHS process and system_server hooks. */
public final class XhsImageFullscreenContract {
    public static final String ACTION_VIEWER_STATE =
            "io.github.pigerzhu.onelab.action.XHS_IMAGE_VIEWER_STATE";
    public static final String EXTRA_VISIBLE = "visible";

    private XhsImageFullscreenContract() {
    }
}
