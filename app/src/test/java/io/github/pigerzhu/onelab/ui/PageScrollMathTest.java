package io.github.pigerzhu.onelab.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Bounds for the inset-aware page scrolling. */
public final class PageScrollMathTest {
    @Test
    public void maxScrollNeverExceedsTheContent() {
        assertEquals(400, PageScrollMath.maxScroll(2000, 1600));
        // Content shorter than the viewport cannot scroll at all.
        assertEquals(0, PageScrollMath.maxScroll(1600, 1600));
        assertEquals(0, PageScrollMath.maxScroll(900, 1600));
        // Degenerate inputs stay safe instead of producing a huge scroll range.
        assertEquals(0, PageScrollMath.maxScroll(-1, 1600));
        assertEquals(0, PageScrollMath.maxScroll(2000, 0));
    }

    @Test
    public void imeHiddenTargetStaysInsideTheShrunkenContent() {
        // Before the keyboard hides, the page may sit at 500; the shrunken content only
        // allows 200, and the target must park exactly at the new maximum.
        assertEquals(200, PageScrollMath.clampScroll(500, 200));
        // It must never jump to the top when the position is still valid.
        assertEquals(150, PageScrollMath.clampScroll(150, 200));
        // Negative targets (input near the top of the content) stay at the top.
        assertEquals(0, PageScrollMath.clampScroll(-80, 200));
    }
}
