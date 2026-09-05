package io.github.pigerzhu.onelab.system;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class Shell {
    private static final long COMMAND_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(15);

    private Shell() {
    }

    public static boolean runSu(String command) {
        return runProcess(Arrays.asList("su", "-c", command), COMMAND_TIMEOUT_MS)
                .completedSuccessfully();
    }

    public static String runSuForOutput(String command) {
        return runSuForOutput(command, false);
    }

    public static boolean runSuInMasterMount(String command) {
        return runProcess(Arrays.asList("su", "-mm", "-c", command), COMMAND_TIMEOUT_MS)
                .completedSuccessfully();
    }

    public static String runSuInMasterMountForOutput(String command) {
        return runSuForOutput(command, true);
    }

    private static String runSuForOutput(String command, boolean masterMount) {
        List<String> args = masterMount
                ? Arrays.asList("su", "-mm", "-c", command)
                : Arrays.asList("su", "-c", command);
        ProcessResult result = runProcess(args, COMMAND_TIMEOUT_MS);
        if (!result.completedSuccessfully()) return null;
        String value = result.output().trim();
        return value.isEmpty() || "null".equals(value) ? null : value;
    }

    static ProcessResult runProcess(List<String> command, long timeoutMs) {
        Process process = null;
        Thread outputReader = null;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            InputStream stream = process.getInputStream();
            outputReader = new Thread(() -> drain(stream, output), "onelab-process-output");
            outputReader.setDaemon(true);
            outputReader.start();

            boolean completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
            }
            outputReader.join(TimeUnit.SECONDS.toMillis(1));
            return new ProcessResult(
                    completed ? process.exitValue() : -1,
                    output.toString(StandardCharsets.UTF_8.name()),
                    !completed);
        } catch (Exception ignored) {
            if (process != null) process.destroyForcibly();
            if (ignored instanceof InterruptedException) Thread.currentThread().interrupt();
            return new ProcessResult(-1, output.toString(), false);
        }
    }

    private static void drain(InputStream stream, ByteArrayOutputStream output) {
        byte[] buffer = new byte[8192];
        try (InputStream input = stream) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                synchronized (output) {
                    output.write(buffer, 0, count);
                }
            }
        } catch (Exception ignored) {
            // Process failures are represented by their exit status or timeout.
        }
    }

    static final class ProcessResult {
        private final int exitCode;
        private final String output;
        private final boolean timedOut;

        ProcessResult(int exitCode, String output, boolean timedOut) {
            this.exitCode = exitCode;
            this.output = output;
            this.timedOut = timedOut;
        }

        boolean completedSuccessfully() { return !timedOut && exitCode == 0; }
        String output() { return output; }
        boolean timedOut() { return timedOut; }
    }
}
