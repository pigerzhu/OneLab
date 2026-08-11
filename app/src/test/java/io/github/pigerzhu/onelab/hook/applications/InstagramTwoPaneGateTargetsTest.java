package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.List;

public class InstagramTwoPaneGateTargetsTest {
    @Test
    public void acceptsOneSemanticGateForAKey() {
        assertEquals("gate", InstagramTwoPaneGateTargets.requireUnique(
                36325123993916442L, List.of("gate")));
    }

    @Test
    public void rejectsMissingOrAmbiguousSemanticGates() {
        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> InstagramTwoPaneGateTargets.requireUnique(
                        36325123993916442L, List.of()));
        assertEquals(
                "Expected one Instagram gate for key 36325123993916442, found 0",
                missing.getMessage());

        assertThrows(IllegalStateException.class,
                () -> InstagramTwoPaneGateTargets.requireUnique(
                        36325123993916442L, List.of("first", "second")));
    }
}
