package io.github.pigerzhu.onelab.system;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import io.github.pigerzhu.onelab.diagnostics.DiagnosticCrashSections;
import io.github.pigerzhu.onelab.diagnostics.DiagnosticLogFilter;

/** Bounded historical system evidence supplements the live log after a UI process exits. */
public final class DiagnosticFailureHistory {
    private DiagnosticFailureHistory() {}

    public static void collect(Context context) throws IOException {
        File directory = DiagnosticCapture.directory(context);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("capture directory");
        StringBuilder exits = new StringBuilder("Historical records: compare exit timestamps with the recording interval.\n");
        for (String process : new String[]{"com.android.systemui", "com.sec.android.app.launcher", "io.github.pigerzhu.onelab"}) {
            exits.append("\n[").append(process).append("]\n")
                    .append(result("dumpsys activity exit-info " + process + " | tail -n 500"));
        }
        write(directory, "process-exits.txt", exits.toString());
        StringBuilder crashes = new StringBuilder("Bounded DropBox history; absence does not prove no crash.\n");
        for (String tag : new String[]{"system_app_crash", "system_app_anr", "system_app_native_crash",
                "system_server_crash", "system_server_watchdog", "SYSTEM_TOMBSTONE"}) {
            crashes.append("\n[").append(tag).append("]\n").append(DiagnosticCrashSections.filter(
                    Shell.runSuForOutput("dumpsys dropbox --print " + tag + " | tail -n 4000")));
        }
        write(directory, "crash-history.txt", crashes.toString());
        write(directory, "last-anr.txt", DiagnosticCrashSections.filter(Shell.runSuForOutput("dumpsys activity lastanr")));
        write(directory, "fold-state.txt", result("cmd device_state print-state; wm size; "
                + "printf 'systemui='; pidof com.android.systemui; printf 'launcher='; pidof com.sec.android.app.launcher; "
                + "printf 'system_server='; pidof system_server; cat /proc/uptime; "
                + "getprop sys.boot.reason; getprop ro.boot.bootreason"));
    }

    private static String result(String command) {
        String result = Shell.runSuForOutput(command);
        return result == null ? "status=unavailable\n" : result + '\n';
    }

    private static void write(File directory, String name, String value) throws IOException {
        Files.write(new File(directory, name).toPath(), DiagnosticLogFilter.redact(value).getBytes(StandardCharsets.UTF_8));
    }
}
