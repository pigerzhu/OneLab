package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LarkPaneMeasureLocatorTest {
    private enum Width {
        AVERAGE_LEVEL
    }

    private static final class LayoutParams {
    }

    private static final class UniqueGroup {
        private int left(int totalWidth, LayoutParams params, boolean firstChild) {
            return totalWidth;
        }

        private int right(int totalWidth, Width width) {
            return totalWidth;
        }
    }

    private static final class AmbiguousGroup {
        private int left(int totalWidth, LayoutParams params, boolean firstChild) {
            return totalWidth;
        }

        private int anotherLeft(int totalWidth, LayoutParams params, boolean firstChild) {
            return totalWidth;
        }

        private int right(int totalWidth, Width width) {
            return totalWidth;
        }
    }

    @Test
    public void locatesBothPaneMeasureMethodsWithoutUsingTheirNames() {
        LarkPaneMeasureLocator.Targets targets =
                LarkPaneMeasureLocator.findUnique(UniqueGroup.class, Width.class);

        assertEquals("left", targets.left.getName());
        assertEquals("right", targets.right.getName());
    }

    @Test
    public void rejectsAnAmbiguousMeasureMethod() {
        assertNull(LarkPaneMeasureLocator.findUnique(AmbiguousGroup.class, Width.class));
    }

    @Test
    public void assignsComplementaryWidthsToBothPanes() {
        assertEquals(742, LarkPaneWidthPolicy.width(1856, 0.4f, true));
        assertEquals(1114, LarkPaneWidthPolicy.width(1856, 0.4f, false));
    }
}
