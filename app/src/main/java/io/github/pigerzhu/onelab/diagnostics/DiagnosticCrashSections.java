package io.github.pigerzhu.onelab.diagnostics;

/** Keeps complete related DropBox entries, including their stack traces. */
public final class DiagnosticCrashSections {
    private DiagnosticCrashSections() {}

    public static String filter(String raw) {
        if (raw == null) return "status=unavailable\n";
        StringBuilder result = new StringBuilder();
        for (String entry : raw.split("(?m)(?=^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})")) {
            if (DiagnosticLogFilter.mentionsTarget(entry)) result.append(DiagnosticLogFilter.redact(entry));
        }
        return result.length() == 0 ? "status=no_related_entries_in_retained_history\n" : result.toString();
    }
}
