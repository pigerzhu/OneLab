package io.github.pigerzhu.onelab.hook.applications;

final class XhsViewerTapPolicy {
    private final float touchSlopSquared;
    private final long longPressTimeoutMillis;

    private boolean tracking;
    private boolean rejected;
    private float downX;
    private float downY;
    private long downTimeMillis;

    XhsViewerTapPolicy(float touchSlop, long longPressTimeoutMillis) {
        this.touchSlopSquared = touchSlop * touchSlop;
        this.longPressTimeoutMillis = longPressTimeoutMillis;
    }

    void onDown(float x, float y, long eventTimeMillis) {
        tracking = true;
        rejected = false;
        downX = x;
        downY = y;
        downTimeMillis = eventTimeMillis;
    }

    void onMove(float x, float y, int pointerCount) {
        if (!tracking) return;
        if (pointerCount != 1 || movedPastSlop(x, y)) rejected = true;
    }

    void onCancel() {
        tracking = false;
        rejected = true;
    }

    boolean onUp(float x, float y, long eventTimeMillis) {
        if (!tracking) return false;
        boolean accepted = !rejected
                && !movedPastSlop(x, y)
                && eventTimeMillis - downTimeMillis < longPressTimeoutMillis;
        tracking = false;
        return accepted;
    }

    private boolean movedPastSlop(float x, float y) {
        float deltaX = x - downX;
        float deltaY = y - downY;
        return deltaX * deltaX + deltaY * deltaY > touchSlopSquared;
    }
}
