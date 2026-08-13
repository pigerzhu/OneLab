package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class HupuEmbeddingStatePolicyTest {
    @Test
    public void missingSettingPreservesNativeEnabledState() {
        assertTrue(HupuEmbeddingStatePolicy.isEnabled(null));
    }

    @Test
    public void explicitZeroDisablesEmbedding() {
        assertFalse(HupuEmbeddingStatePolicy.isEnabled("0"));
    }

    @Test
    public void explicitOneEnablesEmbedding() {
        assertTrue(HupuEmbeddingStatePolicy.isEnabled("1"));
    }
}
