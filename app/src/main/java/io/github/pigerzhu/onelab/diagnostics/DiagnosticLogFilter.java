package io.github.pigerzhu.onelab.diagnostics;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Selects related processes and preserves crash preambles before their process is named. */
public final class DiagnosticLogFilter {
    private static final Pattern LINE = Pattern.compile(
            "^\\d+\\.\\d+\\s+(\\d+)\\s+\\d+\\s+[VDIWEFA]\\s+([^:]+):.*$");
    private static final Pattern PROCESS_START = Pattern.compile(
            "\\[\\s*\\d+\\s*,\\s*(\\d+)\\s*,\\s*\\d+\\s*,\\s*([^,\\]]+)");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(token|cookie|authorization|android_id|serial|imei|imsi|bssid|ssid)\\s*[:=]\\s*[^\\s,;]+");
    private static final Pattern EMAIL = Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?86[- ]?)?1[3-9]\\d{9}(?!\\d)");
    private final Set<String> targetPids = new HashSet<>();
    private final Set<String> crashPids = new HashSet<>();
    private final Map<String, ArrayDeque<String>> preambles = new LinkedHashMap<>();

    public synchronized void setTargetPids(String pids) {
        targetPids.clear();
        for (String pid : pids.trim().split("\\s+")) {
            if (pid.matches("\\d+")) targetPids.add(pid);
        }
    }

    public synchronized String accept(String line) {
        Matcher matcher = LINE.matcher(line);
        if (!matcher.matches()) return "";
        String pid = matcher.group(1);
        String tag = matcher.group(2).trim();
        boolean crash = tag.equals("AndroidRuntime") || tag.equals("DEBUG");
        boolean namesTarget = mentionsTarget(line);
        if (tag.equals("am_proc_start")) {
            Matcher started = PROCESS_START.matcher(line);
            if (started.find() && mentionsTarget(started.group(2))) targetPids.add(started.group(1));
        }
        if (crash && namesTarget) {
            crashPids.add(pid);
            ArrayDeque<String> preamble = preambles.remove(pid);
            StringBuilder result = new StringBuilder();
            if (preamble != null) for (String previous : preamble) result.append(previous).append('\n');
            return redact(result.append(line).append('\n').toString());
        }
        if (targetPids.contains(pid) || (crash && crashPids.contains(pid)) || namesTarget
                || tag.startsWith("OneLab") || tag.equals("DeviceStateManagerService")
                || tag.equals("DisplayManagerService") || tag.equals("LauncherProxyService")
                || tag.equals("OverviewProxyService") || tag.equals("RecentsAnimation")
                || tag.equals("Watchdog") || tag.equals("RescueParty")
                || tag.equals("SurfaceFlinger") || tag.equals("ShellTransitions")) {
            return redact(line) + '\n';
        }
        if (crash) {
            ArrayDeque<String> pending = preambles.get(pid);
            if (pending == null) {
                if (preambles.size() >= 8) preambles.remove(preambles.keySet().iterator().next());
                pending = new ArrayDeque<>();
                preambles.put(pid, pending);
            }
            if (pending.size() == 32) pending.removeFirst();
            pending.addLast(line);
        }
        return "";
    }

    public static boolean mentionsTarget(String text) {
        return text.contains("com.android.systemui") || text.contains("com.sec.android.app.launcher")
                || text.contains("io.github.pigerzhu.onelab") || text.contains("system_server")
                || text.contains("com.samsung.android.app.homestar") || text.contains("com.samsung.android.goodlock");
    }

    public static String redact(String text) {
        return EMAIL.matcher(PHONE.matcher(SECRET.matcher(text).replaceAll("$1=<redacted>"))
                .replaceAll("<phone>")).replaceAll("<email>");
    }
}
