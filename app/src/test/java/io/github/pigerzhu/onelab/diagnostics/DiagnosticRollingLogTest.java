package io.github.pigerzhu.onelab.diagnostics;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class DiagnosticRollingLogTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void flushesImmediatelyAndRetainsBoundedNewestSegments() throws Exception {
        File directory = folder.newFolder();
        try (DiagnosticRollingLog log = new DiagnosticRollingLog(directory, "events", 10, 2)) {
            log.write("first\n");
            assertEquals("first\n", new String(Files.readAllBytes(new File(directory, "events.0.log").toPath()), java.nio.charset.StandardCharsets.UTF_8));
            log.write("second\n");
            log.write("third\n");
            log.write("fourth\n");
        }
        assertFalse(new File(directory, "events.2.log").exists());
        String combined = DiagnosticRollingLog.read(directory, "events", 2);
        assertFalse(combined.contains("first"));
        assertTrue(combined.contains("third"));
        assertTrue(combined.contains("fourth"));
    }

    @Test public void reopensWithoutDiscardingPreviousCapture() throws Exception {
        File directory = folder.newFolder();
        try (DiagnosticRollingLog log = new DiagnosticRollingLog(directory, "events", 100, 2)) { log.write("before\n"); }
        try (DiagnosticRollingLog log = new DiagnosticRollingLog(directory, "events", 100, 2)) { log.write("after\n"); }
        assertEquals("before\nafter\n", DiagnosticRollingLog.read(directory, "events", 2));
    }
}
