package io.github.pigerzhu.onelab.diagnostics;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

import io.github.pigerzhu.onelab.system.Shell;

/** Collects boot-persistent hook evidence needed to diagnose firmware compatibility. */
final class RuntimeCompatibilityReport {
    private static final int MAX_SOURCE_LINES = 20_000;
    private static final int MAX_OUTPUT_LINES = 1_500;
    private static final String MODULE_LOG_COMMAND =
            "toybox cat /data/adb/lspd/log/modules_*.log"
                    + " | toybox tail -n " + MAX_SOURCE_LINES;
    private static final String ENVIRONMENT_COMMAND =
            "printf 'oneui_version_raw='; getprop ro.build.version.oneui; "
                    + "printf 'build_incremental='; getprop ro.build.version.incremental; "
                    + "printf 'sdhms_service='; service check sdhms; "
                    + "printf 'sdhms_process_pid='; pidof com.sec.android.sdhms";

    private RuntimeCompatibilityReport() {
    }

    static Result collect(Context context) {
        String persistentLog = Shell.runSuForOutput(MODULE_LOG_COMMAND);
        String filteredLog = filterHookLog(persistentLog);
        String environment = Shell.runSuForOutput(ENVIRONMENT_COMMAND);
        String oneUiVersion = environmentValue(environment, "oneui_version_raw");
        String incremental = environmentValue(environment, "build_incremental");
        String sdhmsService = environmentValue(environment, "sdhms_service");
        String sdhmsPid = environmentValue(environment, "sdhms_process_pid");

        StringBuilder summary = new StringBuilder();
        summary.append("oneui_version_raw=").append(oneUiVersion).append('\n');
        summary.append("build_incremental=").append(incremental).append('\n');
        summary.append("expected_split_branch=")
                .append(expectedSplitBranch(oneUiVersion)).append('\n');
        summary.append("sdhms_service=").append(sdhmsService).append('\n');
        summary.append("sdhms_process_pid=").append(sdhmsPid).append('\n');
        summary.append("persistent_hook_log=")
                .append(persistentLog == null ? "unavailable" : "available").append('\n');
        appendRuntimeFreshness(context, summary);
        summary.append('\n');

        appendEvidence(
                summary,
                "split.controller",
                filteredLog,
                "OneLab/SamsungSplitRules: installed ");
        appendEvidence(
                summary,
                "split.controller_mismatch",
                filteredLog,
                "One UI version/controller mismatch");
        appendEvidence(
                summary,
                "split.rule_injection",
                filteredLog,
                " split rules for ");
        appendFailureCount(
                summary,
                "split.failures",
                filteredLog,
                "OneLab/SamsungSplitRules",
                new String[]{"failed", "unavailable"});

        appendEvidence(
                summary,
                "sdhms.profile",
                filteredLog,
                "Matched SDHMS profile:");
        appendEvidence(
                summary,
                "sdhms.binder_hook",
                filteredLog,
                "Hooked SDHMS binder thermal delta entry");
        appendEvidence(
                summary,
                "sdhms.thermal_gates",
                filteredLog,
                "Hooked SDHMS ThermalGuardian gates");
        appendEvidence(
                summary,
                "sdhms.controller_hook",
                filteredLog,
                "Hooked SDHMS ThermalGuardian controller");
        appendEvidence(
                summary,
                "sdhms.gpu_cap_hook",
                filteredLog,
                "Hooked SDHMS GPUFreqMax cap class");
        appendEvidence(
                summary,
                "sdhms.cpu_cap_hook",
                filteredLog,
                "Hooked SDHMS CPUFreqMax cap class");
        appendEvidence(
                summary,
                "sdhms.little_cpu_cap_hook",
                filteredLog,
                "Hooked SDHMS LittleCPUFreqMax cap class");
        appendEvidence(
                summary,
                "sdhms.multiwindow_hook",
                filteredLog,
                "Hooked SDHMS SSRM multi-window limit");
        appendEvidence(
                summary,
                "sdhms.gpu_range_active",
                filteredLog,
                "GPU range DVFS active:");
        appendEvidence(
                summary,
                "sdhms.gpu_range_min_unavailable",
                filteredLog,
                "GPU range DVFS minimum unavailable");
        appendFailureCount(
                summary,
                "sdhms.failures",
                filteredLog,
                "SDHMS",
                new String[]{"failed", "unavailable"});

        summary.append('\n')
                .append("note=not_observed does not mean failure; it means the current "
                        + "persistent log contains no evidence.")
                .append("Reboot after installing the module and enabling its scope, "
                        + "then reproduce the issue and generate the report again.\n");
        return new Result(summary.toString(), hookLogFile(filteredLog, persistentLog));
    }

    private static void appendRuntimeFreshness(Context context, StringBuilder output) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0);
            long bootedAt = System.currentTimeMillis() - SystemClock.elapsedRealtime();
            output.append("module_last_update_epoch_ms=")
                    .append(info.lastUpdateTime).append('\n');
            output.append("device_boot_epoch_ms=").append(bootedAt).append('\n');
            output.append("module_updated_after_boot=")
                    .append(info.lastUpdateTime > bootedAt).append('\n');
        } catch (Exception ignored) {
            output.append("module_updated_after_boot=unknown\n");
        }
    }

    static String filterHookLog(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String[] lines = raw.split("\\R");
        List<String> included = new ArrayList<>();
        int failureContextLines = 0;
        for (String line : lines) {
            boolean relevant = isRelevant(line);
            if (!relevant && failureContextLines <= 0) continue;
            included.add(line);
            if (relevant && isFailureLine(line)) {
                failureContextLines = 12;
            } else if (!relevant) {
                failureContextLines--;
            }
            if (included.size() > MAX_OUTPUT_LINES) {
                included.remove(0);
            }
        }
        return String.join("\n", included);
    }

    private static boolean isRelevant(String line) {
        return line.contains("OneLab")
                || line.contains("io.github.pigerzhu.onelab");
    }

    private static boolean isFailureLine(String line) {
        String lower = line.toLowerCase();
        return lower.contains("failed")
                || lower.contains("failure")
                || lower.contains("unavailable")
                || lower.contains("mismatch");
    }

    private static String expectedSplitBranch(String rawOneUiVersion) {
        try {
            return Integer.parseInt(rawOneUiVersion.trim()) >= 80500
                    ? "oneui_8_5"
                    : "legacy";
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private static String environmentValue(String environment, String key) {
        if (environment == null) return "unavailable";
        String prefix = key + "=";
        for (String line : environment.split("\\R")) {
            if (line.startsWith(prefix)) {
                String value = line.substring(prefix.length()).trim();
                return value.isEmpty() ? "unavailable" : value;
            }
        }
        return "unavailable";
    }

    private static void appendEvidence(
            StringBuilder output,
            String key,
            String log,
            String marker
    ) {
        String line = lastLineContaining(log, marker);
        output.append(key).append('=')
                .append(line == null ? "not_observed" : "observed")
                .append('\n');
        if (line != null) {
            output.append(key).append(".evidence=")
                    .append(compact(line)).append('\n');
        }
    }

    private static void appendFailureCount(
            StringBuilder output,
            String key,
            String log,
            String ownerMarker,
            String[] failureMarkers
    ) {
        int count = 0;
        String latest = null;
        if (log != null && !log.isEmpty()) {
            for (String line : log.split("\\R")) {
                if (!line.contains(ownerMarker)) continue;
                String lower = line.toLowerCase();
                for (String marker : failureMarkers) {
                    if (lower.contains(marker)) {
                        count++;
                        latest = line;
                        break;
                    }
                }
            }
        }
        output.append(key).append('=').append(count).append('\n');
        if (latest != null) {
            output.append(key).append(".latest=")
                    .append(compact(latest)).append('\n');
        }
    }

    private static String lastLineContaining(String log, String marker) {
        if (log == null || log.isEmpty()) return null;
        String match = null;
        for (String line : log.split("\\R")) {
            if (line.contains(marker)) match = line;
        }
        return match;
    }

    private static String compact(String line) {
        return line.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String hookLogFile(String filteredLog, String rawLog) {
        if (!filteredLog.isEmpty()) return filteredLog + '\n';
        if (rawLog == null) {
            return "status=unavailable\n"
                    + "reason=cannot read the LSPosed persistent module log.\n";
        }
        return "status=empty\n"
                + "reason=the persistent module log contains no OneLab entries; "
                + "check that the module is enabled, review its scope and reboot.\n";
    }

    static final class Result {
        final String compatibility;
        final String hookLog;

        Result(String compatibility, String hookLog) {
            this.compatibility = compatibility;
            this.hookLog = hookLog;
        }
    }
}
