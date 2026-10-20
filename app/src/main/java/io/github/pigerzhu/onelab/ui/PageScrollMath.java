package io.github.pigerzhu.onelab.ui;

/** Pure scroll bounds for inset-aware pages, kept JVM-testable. */
public final class PageScrollMath {
    private PageScrollMath() {
    }

    /**
     * Highest scroll offset that still shows real content: the content's full layout
     * range (child height plus both paddings) minus the viewport. Never negative, so a
     * shrunken layout cannot leave the page parked past its content.
     */
    public static int maxScroll(int contentRange, int viewportHeight) {
        if (contentRange < 0 || viewportHeight <= 0) return 0;
        return Math.max(0, contentRange - viewportHeight);
    }

    /** Clamps a desired scroll offset into [0, maxScroll]. */
    public static int clampScroll(int desired, int maxScroll) {
        return Math.max(0, Math.min(desired, maxScroll));
    }
}
