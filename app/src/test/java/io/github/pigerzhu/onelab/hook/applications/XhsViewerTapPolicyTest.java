package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class XhsViewerTapPolicyTest {
    private static final float TOUCH_SLOP = 12f;
    private static final long LONG_PRESS_TIMEOUT_MS = 500L;

    @Test
    public void acceptsShortSinglePointerTapWithinTouchSlop() {
        XhsViewerTapPolicy policy = policy();
        policy.onDown(100f, 200f, 1_000L);
        policy.onMove(106f, 205f, 1);

        assertTrue(policy.onUp(106f, 205f, 1_180L));
    }

    @Test
    public void rejectsDragPastTouchSlop() {
        XhsViewerTapPolicy policy = policy();
        policy.onDown(100f, 200f, 1_000L);
        policy.onMove(113f, 200f, 1);

        assertFalse(policy.onUp(113f, 200f, 1_180L));
    }

    @Test
    public void rejectsGestureThatEverHadMultiplePointers() {
        XhsViewerTapPolicy policy = policy();
        policy.onDown(100f, 200f, 1_000L);
        policy.onMove(100f, 200f, 2);

        assertFalse(policy.onUp(100f, 200f, 1_180L));
    }

    @Test
    public void rejectsCancelledGesture() {
        XhsViewerTapPolicy policy = policy();
        policy.onDown(100f, 200f, 1_000L);
        policy.onCancel();

        assertFalse(policy.onUp(100f, 200f, 1_180L));
    }

    @Test
    public void rejectsLongPress() {
        XhsViewerTapPolicy policy = policy();
        policy.onDown(100f, 200f, 1_000L);

        assertFalse(policy.onUp(100f, 200f, 1_500L));
    }

    @Test
    public void rejectsUpWithoutDownAndConsumesSuccessfulTap() {
        XhsViewerTapPolicy policy = policy();
        assertFalse(policy.onUp(100f, 200f, 1_100L));

        policy.onDown(100f, 200f, 1_000L);
        assertTrue(policy.onUp(100f, 200f, 1_100L));
        assertFalse(policy.onUp(100f, 200f, 1_110L));
    }

    private static XhsViewerTapPolicy policy() {
        return new XhsViewerTapPolicy(TOUCH_SLOP, LONG_PRESS_TIMEOUT_MS);
    }
}
