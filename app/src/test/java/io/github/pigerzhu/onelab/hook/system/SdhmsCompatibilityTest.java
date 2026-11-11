package io.github.pigerzhu.onelab.hook.system;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SdhmsCompatibilityTest {
    @Test
    public void serviceProfileRequiresThermalBinderContract() {
        SdhmsCompatibility.Profile profile = new SdhmsCompatibility.Profile(
                "test", ThermalService.class.getName(), "test.Controller", "F", "t", "D", "E",
                "test.Gpu", "test.Cpu", "test.Little", "test.Limiter", "o");

        assertTrue(SdhmsCompatibility.matchesServiceContract(ThermalService.class, profile));
        assertFalse(SdhmsCompatibility.matchesServiceContract(RunnableService.class, profile));
    }

    @Test
    public void ignoresOldServiceNameWhenItIsOnlyAnUnrelatedRunnable() {
        SdhmsCompatibility.Profile profile = SdhmsCompatibility.detect(
                getClass().getClassLoader());

        org.junit.Assert.assertNotNull(profile);
        org.junit.Assert.assertEquals("One UI 9", profile.label);
        org.junit.Assert.assertEquals("v4.g", profile.serviceClassName);
        org.junit.Assert.assertEquals("k5.w4", profile.controllerClassName);
        org.junit.Assert.assertEquals("k5.e2", profile.gpuCapClassName);
        org.junit.Assert.assertEquals("k5.b1", profile.cpuCapClassName);
        org.junit.Assert.assertEquals("k5.a1", profile.littleCpuCapClassName);
        org.junit.Assert.assertEquals("k5.m5", profile.hiddenLimiterClassName);
    }

    private static final class ThermalService {
        public boolean D() { return true; }
        public boolean G(int flags) { return flags != 0; }
        public boolean H(int delta) { return delta == 0; }
        public boolean I(String packageName, int delta) { return packageName != null && delta == 0; }
    }

    private static final class RunnableService implements Runnable {
        @Override
        public void run() {
        }
    }
}
