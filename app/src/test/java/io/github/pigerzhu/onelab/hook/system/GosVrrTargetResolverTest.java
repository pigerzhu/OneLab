package io.github.pigerzhu.onelab.hook.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Method;

import org.junit.Test;

public final class GosVrrTargetResolverTest {
    @Test
    public void findsUpdateMethodWhenVrrCoreMovesBetweenCandidateClasses() {
        Method oneUi8 = GosVrrTargetResolver.findUpdateMethod(
                ConstantsOnly.class, OneUi8Core.class);
        Method oneUi85 = GosVrrTargetResolver.findUpdateMethod(
                OneUi85Core.class, ConstantsOnly.class);

        assertEquals("n", oneUi8.getName());
        assertEquals("o", oneUi85.getName());
    }

    @Test
    public void rejectsAmbiguousOrIncompatibleMethodShapes() {
        assertNull(GosVrrTargetResolver.findUpdateMethod(AmbiguousCore.class));
        assertNull(GosVrrTargetResolver.findUpdateMethod(IncompatibleCore.class));
    }

    private static final class ConstantsOnly {
        int[] rates() {
            return new int[]{120, 60, 30};
        }
    }

    private static final class OneUi8Core {
        public void n(int refreshRate, String packageName) {
        }
    }

    private static final class OneUi85Core {
        public void o(int refreshRate, String packageName) {
        }
    }

    private static final class AmbiguousCore {
        public void a(int refreshRate, String packageName) {
        }

        public void b(int refreshRate, String packageName) {
        }
    }

    private static final class IncompatibleCore {
        public int update(int refreshRate, String packageName) {
            return refreshRate;
        }

        public void update(String packageName, int refreshRate) {
        }
    }
}
