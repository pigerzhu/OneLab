package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

public final class KuaishouLargeScreenTargetsTest {
    @Test
    public void selectsOnlyStaticNoArgumentBooleanGate() throws Exception {
        Method expected = Candidates.class.getDeclaredMethod("gate");
        Method selected = KuaishouLargeScreenTargets.requireUniqueGate(List.of(
                Candidates.class.getDeclaredMethod("instanceGate"),
                Candidates.class.getDeclaredMethod("gateWithArgument", int.class),
                Candidates.class.getDeclaredMethod("notBoolean"),
                expected));

        assertEquals(expected, selected);
    }

    @Test
    public void rejectsAmbiguousStaticBooleanGates() throws Exception {
        List<Method> candidates = List.of(
                Candidates.class.getDeclaredMethod("gate"),
                Candidates.class.getDeclaredMethod("secondGate"));

        assertThrows(IllegalStateException.class,
                () -> KuaishouLargeScreenTargets.requireUniqueGate(candidates));
    }

    private static final class Candidates {
        static boolean gate() {
            return true;
        }

        static boolean secondGate() {
            return false;
        }

        boolean instanceGate() {
            return true;
        }

        static boolean gateWithArgument(int ignored) {
            return true;
        }

        static int notBoolean() {
            return 1;
        }
    }
}
