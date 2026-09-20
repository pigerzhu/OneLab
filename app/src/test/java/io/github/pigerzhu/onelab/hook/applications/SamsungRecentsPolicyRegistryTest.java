package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SamsungRecentsPolicyRegistryTest {
    @Test
    public void keepsEqualPolicyAndFlowObjectsIsolatedByIdentity() {
        SamsungRecentsPolicyRegistry registry = new SamsungRecentsPolicyRegistry();
        Object firstPolicy = new AlwaysEqual();
        Object secondPolicy = new AlwaysEqual();
        Object firstFlow = new AlwaysEqual();
        Object secondFlow = new AlwaysEqual();

        SamsungRecentsPolicyRegistry.Entry first = registry.register(
                firstPolicy, firstFlow, new Object(), new Object(), new Object(), 0);
        SamsungRecentsPolicyRegistry.Entry second = registry.register(
                secondPolicy, secondFlow, new Object(), new Object(), new Object(), 5);

        assertSame(first, registry.findByPolicy(firstPolicy));
        assertSame(second, registry.findByPolicy(secondPolicy));
        assertSame(first, registry.findByWritableState(firstFlow));
        assertSame(second, registry.findByWritableState(secondFlow));
        assertEquals(2, registry.snapshot().size());
    }

    @Test
    public void keepsPendingValuesAndOverrideFlagsPerEntry() {
        SamsungRecentsPolicyRegistry registry = new SamsungRecentsPolicyRegistry();
        SamsungRecentsPolicyRegistry.Entry first = registry.register(
                new Object(), new Object(), new Object(), new Object(), new Object(), 0);
        SamsungRecentsPolicyRegistry.Entry second = registry.register(
                new Object(), new Object(), new Object(), new Object(), new Object(), 5);

        first.setPendingHomeUpLayout(1);
        first.setWritingOverride(true);
        second.setPendingHomeUpLayout(5);

        assertEquals(Integer.valueOf(1), first.takePendingHomeUpLayout());
        assertEquals(Integer.valueOf(5), second.takePendingHomeUpLayout());
        assertTrue(first.isWritingOverride());
        assertFalse(second.isWritingOverride());
    }

    @Test
    public void keepsDisplayTypePerPolicyEntry() {
        SamsungRecentsPolicyRegistry registry = new SamsungRecentsPolicyRegistry();
        SamsungRecentsPolicyRegistry.Entry main = registry.register(
                new Object(), new Object(), new Object(), new Object(), new Object(), 0);
        SamsungRecentsPolicyRegistry.Entry cover = registry.register(
                new Object(), new Object(), new Object(), new Object(), new Object(), 5);

        assertEquals(0, main.displayType());
        assertEquals(5, cover.displayType());
    }

    private static final class AlwaysEqual {
        @Override
        public boolean equals(Object ignored) {
            return true;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }
}
