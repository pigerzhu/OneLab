package io.github.pigerzhu.onelab.system;

import android.content.Context;
import android.content.res.Configuration;
import android.os.SystemClock;
import android.provider.Settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import io.github.pigerzhu.onelab.contract.SettingsKeys;
import io.github.pigerzhu.onelab.diagnostics.DiagnosticLogFilter;
import io.github.pigerzhu.onelab.diagnostics.DiagnosticRollingLog;

/** Root logcat capture owned by the explicit diagnostic foreground service. */
public final class DiagnosticCapture {
    private static volatile Session active;
    private static final String PID_COMMAND = "printf 'systemui='; pidof com.android.systemui; "
            + "printf 'launcher='; pidof com.sec.android.app.launcher; "
            + "printf 'system_server='; pidof system_server; "
            + "printf 'homeup='; pidof com.samsung.android.app.homestar; "
            + "printf 'onelab='; pidof io.github.pigerzhu.onelab; "
            + "printf 'goodlock='; pidof com.samsung.android.goodlock; true";
    public static final int LOG_SEGMENTS = 4;
    public static final int STATE_SEGMENTS = 2;

    private DiagnosticCapture() {}

    public static File directory(Context context) { return new File(context.getFilesDir(), "recents-capture"); }

    /** Called on the diagnostics worker before starting a new recording. */
    public static void clear(Context context) throws IOException {
        stopAndAwait();
        File[] files = directory(context).listFiles();
        if (files != null) for (File file : files) {
            if (file.isFile()) Files.delete(file.toPath());
        }
    }

    public static synchronized boolean isRunning() { return active != null && !active.stopping.get(); }

    public static void start(Context context, Consumer<String> onFailure) throws IOException {
        Session session;
        synchronized (DiagnosticCapture.class) {
            if (isRunning()) return;
            session = new Session(context.getApplicationContext(), onFailure);
            active = session;
        }
        session.start();
    }

    public static void requestStop() {
        Session session = active;
        if (session != null) session.requestStop();
    }

    /** Never call from the main thread. */
    public static void stopAndAwait() throws IOException {
        Session session = active;
        if (session == null) return;
        session.requestStop();
        try {
            if (!session.finished.await(40, TimeUnit.SECONDS)) throw new IOException("capture stop timed out");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException(error);
        }
    }

    private static final class Session {
        final Context context;
        final Consumer<String> onFailure;
        final DiagnosticLogFilter filter = new DiagnosticLogFilter();
        final DiagnosticRollingLog logs;
        final DiagnosticRollingLog states;
        final ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor();
        final AtomicBoolean stopping = new AtomicBoolean();
        final CountDownLatch finished = new CountDownLatch(1);
        volatile Process process;
        volatile String logcatPid;
        Thread reader;

        Session(Context context, Consumer<String> onFailure) throws IOException {
            this.context = context;
            this.onFailure = onFailure;
            File dir = directory(context);
            logs = new DiagnosticRollingLog(dir, "logcat", 2 * 1024 * 1024, LOG_SEGMENTS);
            states = new DiagnosticRollingLog(dir, "state", 512 * 1024, STATE_SEGMENTS);
        }

        void start() {
            reader = new Thread(() -> {
                try {
                    event("capture_started boot_id=" + Shell.runSuForOutput("cat /proc/sys/kernel/random/boot_id")
                            + " boot_reason=" + Shell.runSuForOutput("getprop sys.boot.reason; getprop ro.boot.bootreason"));
                    if (stopping.get()) return;
                    sample();
                    if (stopping.get()) return;
                    process = new ProcessBuilder(RootCommandPath.resolve(), "-c",
                            "echo ONELAB_LOGCAT_PID=$$; exec logcat -b main -b system -b crash -b events -v epoch -T 1")
                            .redirectErrorStream(true).start();
                    if (stopping.get()) { process.destroy(); return; }
                    event("hook_trace_enabled=" + Shell.runSu("settings put global "
                            + SettingsKeys.KEY_RECENTS_DIAGNOSTIC_RECORDING + " 1"));
                    sampler.scheduleWithFixedDelay(this::sample, 3, 3, TimeUnit.SECONDS);
                    try (BufferedReader input = new BufferedReader(new InputStreamReader(
                            process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = input.readLine()) != null) {
                            if (line.startsWith("ONELAB_LOGCAT_PID=")) {
                                String pid = line.substring("ONELAB_LOGCAT_PID=".length()).trim();
                                if (pid.matches("\\d+")) logcatPid = pid;
                                event("logcat_attached pid=" + logcatPid);
                            } else if (line.matches("^\\d+\\.\\d+\\s+.*")) {
                                String selected = filter.accept(line);
                                if (!selected.isEmpty()) logs.write(selected);
                            } else {
                                event("logcat_message=" + line);
                            }
                        }
                    }
                    if (!stopping.get()) fail("logcat_exited");
                } catch (Exception error) {
                    if (!stopping.get()) fail("capture_error=" + error);
                }
            }, "onelab-diagnostic-logcat");
            reader.start();
        }

        void sample() {
            if (stopping.get()) return;
            try {
                String pids = Shell.runSuForOutput(PID_COMMAND);
                if (pids != null) filter.setTargetPids(pids.replaceAll("[^0-9\\s]", " "));
                Configuration config = context.getResources().getConfiguration();
                int display = config.getClass().getField("semDisplayDeviceType").getInt(config);
                event("sample pids={" + (pids == null ? "unavailable" : pids.replace('\n', ';'))
                        + "} app_display=" + display + " size_dp=" + config.screenWidthDp + "x" + config.screenHeightDp
                        + " enabled=" + setting(SettingsKeys.KEY_ENABLE_RECENTS_LAYOUT_PER_DISPLAY)
                        + " main=" + setting(SettingsKeys.KEY_RECENTS_LAYOUT_MAIN)
                        + " cover=" + setting(SettingsKeys.KEY_RECENTS_LAYOUT_COVER)
                        + " runtime=" + setting(SettingsKeys.KEY_RECENTS_LAYOUT_RUNTIME_STATUS));
            } catch (Exception error) { event("sample_error=" + error); }
        }

        String setting(String key) { return Settings.Global.getString(context.getContentResolver(), key); }

        void event(String text) {
            try {
                states.write(DiagnosticLogFilter.redact("epoch_ms=" + System.currentTimeMillis()
                        + " uptime_ms=" + SystemClock.elapsedRealtime() + " " + text + "\n"));
            } catch (IOException ignored) {
                // Capture failure is also represented by missing heartbeat/log segments in the report.
            }
        }

        void fail(String reason) {
            event(reason);
            requestStop();
            onFailure.accept(reason);
        }

        void requestStop() {
            if (!stopping.compareAndSet(false, true)) return;
            sampler.shutdownNow();
            if (reader != null && reader != Thread.currentThread()) reader.interrupt();
            new Thread(() -> {
                try {
                    String pid = logcatPid;
                    if (pid != null) Shell.runSu("kill -TERM " + pid);
                    Process running = process;
                    if (running != null) running.destroy();
                    if (reader != null) reader.join(2500);
                    sampler.awaitTermination(2500, TimeUnit.MILLISECONDS);
                    Shell.runSu("settings put global " + SettingsKeys.KEY_RECENTS_DIAGNOSTIC_RECORDING + " 0");
                    event("capture_stopped");
                    logs.close();
                    states.close();
                } catch (Exception error) { event("stop_error=" + error); }
                finally { finished.countDown(); }
            }, "onelab-diagnostic-stop").start();
        }
    }
}
