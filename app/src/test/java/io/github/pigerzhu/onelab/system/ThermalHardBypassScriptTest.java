package io.github.pigerzhu.onelab.system;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ThermalHardBypassScriptTest {
    @Test
    public void scriptStopsPerformanceThermalsAndAlwaysRestoresThem() {
        String script = ThermalHardBypassScript.build();

        assertTrue(script.contains("stop thermal-engine"));
        assertFalse(script.contains("stop vendor.samsung.hardware.thermal-default"));
        assertTrue(script.contains("pidof vendor.samsung.hardware.thermal-service"));
        assertTrue(script.contains("cpufreq-cpu*|cpu-cluster*|gpu|display-fps"));
        assertTrue(script.contains("echo 0 > \"$c/cur_state\""));
        assertTrue(script.contains("echo disabled > \"$z/mode\""));
        assertTrue(script.contains("trap restore EXIT TERM INT HUP"));
        assertTrue(script.contains("start thermal-engine"));
    }

    @Test
    public void scriptHasIndependentBatteryAndJunctionEmergencyGuard() {
        String script = ThermalHardBypassScript.build();

        assertTrue(script.contains("BATTERY_LIMIT=450"));
        assertTrue(script.contains("JUNCTION_LIMIT=100000"));
        assertTrue(script.contains("settings put global onelab_thermal_hard_bypass 0"));
        assertFalse(script.contains("while true; do echo 0"));
    }
}
