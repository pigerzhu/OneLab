package io.github.pigerzhu.onelab.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class ShellProcessRunnerTest {
    @Test
    public void capturesSuccessfulOutput() {
        Shell.ProcessResult result = Shell.runProcess(
                command("echo onelab"), TimeUnit.SECONDS.toMillis(2));

        assertTrue(result.completedSuccessfully());
        assertEquals("onelab", result.output().trim());
    }

    @Test
    public void drainsOutputLargerThanProcessPipe() {
        Shell.ProcessResult result = Shell.runProcess(
                command("for /L %i in (1,1,20000) do @echo 01234567890123456789"),
                TimeUnit.SECONDS.toMillis(10));

        assertTrue(result.completedSuccessfully());
        assertTrue(result.output().length() > 200_000);
    }

    @Test
    public void timesOutWithoutWaitingForProcessExit() {
        long started = System.nanoTime();
        Shell.ProcessResult result = Shell.runProcess(
                command("ping -n 6 127.0.0.1 >nul"), 100);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertFalse(result.completedSuccessfully());
        assertTrue(result.timedOut());
        assertTrue("elapsed=" + elapsedMs, elapsedMs < 2_000);
    }

    private static java.util.List<String> command(String command) {
        return Arrays.asList("cmd.exe", "/d", "/c", command);
    }
}
