package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.List;

public class XhsCommentHookTargetsTest {
    @Test
    public void acceptsOneOrTwoRouteGates() {
        assertEquals(List.of("one"),
                XhsCommentHookTargets.requireRouteGates(List.of("one")));
        assertEquals(List.of("one", "two"),
                XhsCommentHookTargets.requireRouteGates(List.of("one", "two")));
    }

    @Test
    public void rejectsMissingOrExcessRouteGates() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> XhsCommentHookTargets.requireRouteGates(List.of()));

        assertEquals("Expected one or two XHS route gates, found 0", error.getMessage());
        assertThrows(IllegalStateException.class,
                () -> XhsCommentHookTargets.requireRouteGates(List.of("1", "2", "3")));
    }

    @Test
    public void rejectsAmbiguousScreenGate() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> XhsCommentHookTargets.requireUnique(
                        "screen gate", List.of("first", "second")));

        assertEquals("Expected one XHS screen gate, found 2", error.getMessage());
    }

    @Test
    public void acceptsOneOrTwoDialogFactories() {
        assertEquals(List.of("one"),
                XhsCommentHookTargets.requireFactories(List.of("one")));
        assertEquals(List.of("one", "two"),
                XhsCommentHookTargets.requireFactories(List.of("one", "two")));
    }

    @Test
    public void rejectsMissingOrExcessDialogFactories() {
        assertThrows(IllegalStateException.class,
                () -> XhsCommentHookTargets.requireFactories(List.of()));
        assertThrows(IllegalStateException.class,
                () -> XhsCommentHookTargets.requireFactories(List.of("1", "2", "3")));
    }
}
