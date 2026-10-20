package io.github.pigerzhu.onelab.diagnostics;

import static org.junit.Assert.*;
import org.junit.Test;

public final class DiagnosticCrashSectionsTest {
    @Test public void retainsWholeRelatedEntryWithoutOtherCrashPayloads() {
        String raw = "2026-10-20 12:00:00 system_app_crash (text)\n"
                + "Process: com.example.other\nprivate content\n"
                + "2026-10-20 12:01:00 system_app_crash (text)\n"
                + "Process: com.android.systemui\nFATAL EXCEPTION\nCaused by: bad state\n"
                + "2026-10-20 12:02:00 system_app_crash (text)\nProcess: com.example.other\nother payload\n";
        String filtered = DiagnosticCrashSections.filter(raw);
        assertTrue(filtered.contains("Caused by: bad state"));
        assertFalse(filtered.contains("private content"));
        assertFalse(filtered.contains("other payload"));
    }
}
