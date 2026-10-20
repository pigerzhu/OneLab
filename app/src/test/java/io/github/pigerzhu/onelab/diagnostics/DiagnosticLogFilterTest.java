package io.github.pigerzhu.onelab.diagnostics;

import static org.junit.Assert.*;

import org.junit.Test;

public final class DiagnosticLogFilterTest {
    @Test public void followsSystemUiPidAcrossRestartWithoutKeepingOtherApps() {
        DiagnosticLogFilter filter = new DiagnosticLogFilter();
        filter.setTargetPids("100 200");
        assertTrue(filter.accept("1000.000 100 101 I FoldController: opened").contains("opened"));
        assertEquals("", filter.accept("1000.001 300 301 I Chat: private message"));
        filter.setTargetPids("400 200");
        assertTrue(filter.accept("1001.000 400 401 I NewTag: recovered").contains("recovered"));
    }

    @Test public void keepsFatalHeaderAndWholeStackWhenNewProcessIsIdentified() {
        DiagnosticLogFilter filter = new DiagnosticLogFilter();
        assertEquals("", filter.accept("1000.000 500 501 E AndroidRuntime: FATAL EXCEPTION: main"));
        String identified = filter.accept("1000.001 500 501 E AndroidRuntime: Process: com.android.systemui, PID: 500");
        assertTrue(identified.contains("FATAL EXCEPTION"));
        assertTrue(identified.contains("Process: com.android.systemui"));
        assertTrue(filter.accept("1000.002 500 501 E AndroidRuntime: Caused by: java.lang.IllegalStateException").contains("Caused by"));
    }

    @Test public void keepsNativeCrashContextAndFoldEvents() {
        DiagnosticLogFilter filter = new DiagnosticLogFilter();
        filter.accept("1000.000 600 601 F DEBUG: signal 6 (SIGABRT)");
        assertTrue(filter.accept("1000.001 600 601 F DEBUG: pid: 200, tid: 201 >>> com.sec.android.app.launcher <<<").contains("SIGABRT"));
        assertTrue(filter.accept("1000.002 600 601 F DEBUG: backtrace:").contains("backtrace"));
        assertTrue(filter.accept("1000.003 1000 1001 I DeviceStateManagerService: state=OPENED").contains("OPENED"));
        assertTrue(filter.accept("1000.004 1000 1001 I am_proc_died: [0,200,com.sec.android.app.launcher]").contains("am_proc_died"));
    }

    @Test public void stripsCommonSecretsBeforeDiskWrite() {
        DiagnosticLogFilter filter = new DiagnosticLogFilter();
        String line = filter.accept("1000.000 200 201 I OneLab/RecentsDiagnostic: token=secret email=test@example.com");
        assertFalse(line.contains("secret"));
        assertFalse(line.contains("test@example.com"));
    }

    @Test public void followsRestartImmediatelyFromProcessStartEventBeforeNextSample() {
        DiagnosticLogFilter filter = new DiagnosticLogFilter();
        filter.setTargetPids("100 200");
        filter.accept("1001.000 200 201 I am_proc_start: [0,700,1000,com.android.systemui,service,com.android.systemui/.SystemUIService]");
        assertTrue(filter.accept("1001.001 700 701 I NewTag: first_frame").contains("first_frame"));
    }
}
