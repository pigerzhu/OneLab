package io.github.pigerzhu.onelab.system;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class ThermalHardBypassController {
    private static final String SCRIPT = "/data/local/tmp/onelab-thermal-hard-bypass.sh";
    private static final String PID = "/data/local/tmp/onelab-thermal-hard-bypass/pid";
    private static final String SETTING = "onelab_thermal_hard_bypass";

    private ThermalHardBypassController() {
    }

    public static boolean enable() {
        String encoded = Base64.getEncoder().encodeToString(
                ThermalHardBypassScript.build().getBytes(StandardCharsets.UTF_8));
        String command = "settings put global " + SETTING + " 1; "
                + "printf '%s' '" + encoded + "' | base64 -d > " + SCRIPT + "; "
                + "chmod 700 " + SCRIPT + "; "
                + "nohup sh " + SCRIPT + " >/data/local/tmp/onelab-thermal-hard-bypass.log 2>&1 </dev/null & "
                + "i=0; while [ $i -lt 100 ] && [ \"$(settings get global "
                + "onelab_thermal_hard_bypass_status)\" != active ]; do "
                + "sleep 0.1; i=$((i+1)); done; "
                + "test \"$(settings get global onelab_thermal_hard_bypass_status)\" = active "
                + "&& test -r " + PID + " && kill -0 $(cat " + PID + ")";
        if (Shell.runSu(command)) return true;
        Shell.runSu("settings put global " + SETTING + " 0");
        return false;
    }

    public static boolean disable() {
        String command = "settings put global " + SETTING + " 0; "
                + "i=0; while [ -r " + PID + " ] && [ $i -lt 40 ]; do sleep 0.1; i=$((i+1)); done; "
                + "if [ -r " + PID + " ]; then kill -TERM $(cat " + PID + "); sleep 1; fi; "
                + "test ! -r " + PID;
        return Shell.runSu(command);
    }

    public static String status() {
        String value = Shell.runSuForOutput("settings get global onelab_thermal_hard_bypass_status");
        return value == null ? "unknown" : value;
    }

    public static boolean isActive() {
        return Shell.runSu("test -r " + PID + " && kill -0 $(cat " + PID + ")");
    }

    public static void clearStaleEnabledSetting() {
        Shell.runSu("settings put global " + SETTING + " 0");
    }
}
