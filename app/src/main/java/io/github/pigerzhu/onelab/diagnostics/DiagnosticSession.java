package io.github.pigerzhu.onelab.diagnostics;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.io.IOException;
import io.github.pigerzhu.onelab.feature.diagnostics.DiagnosticRecordingService;
import io.github.pigerzhu.onelab.system.DiagnosticCapture;
import io.github.pigerzhu.onelab.system.DiagnosticFailureHistory;

/** Explicit recording lifecycle, kept separate from report generation. */
public final class DiagnosticSession {
    private static final String START = "session_started_at";
    private static final String STOP = "session_stopped_at";
    private static final String FAILURE = "session_failure";

    private DiagnosticSession() {}

    public static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("onelab_diagnostics", Context.MODE_PRIVATE);
    }

    public static long start(Context context) {
        try { DiagnosticCapture.clear(context); }
        catch (IOException error) { throw new IllegalStateException(error); }
        long now = System.currentTimeMillis();
        preferences(context).edit().putLong(START, now).remove(STOP).remove(FAILURE).commit();
        try {
            context.startForegroundService(new Intent(context, DiagnosticRecordingService.class));
        } catch (RuntimeException error) {
            failed(context, error.toString());
            throw error;
        }
        return now;
    }

    public static long stop(Context context) {
        if (startedAt(context) == 0L) return 0L;
        long now = System.currentTimeMillis();
        try { DiagnosticCapture.stopAndAwait(); }
        catch (IOException error) { throw new IllegalStateException(error); }
        context.stopService(new Intent(context, DiagnosticRecordingService.class));
        preferences(context).edit().putLong(STOP, now).commit();
        try { DiagnosticFailureHistory.collect(context); }
        catch (IOException error) { throw new IllegalStateException(error); }
        return now;
    }

    public static void failed(Context context, String reason) {
        preferences(context).edit().putLong(STOP, System.currentTimeMillis())
                .putString(FAILURE, DiagnosticLogFilter.redact(reason)).commit();
    }

    public static String failure(Context context) { return preferences(context).getString(FAILURE, null); }
    public static long startedAt(Context context) { return preferences(context).getLong(START, 0L); }
    public static long stoppedAt(Context context) { return preferences(context).getLong(STOP, 0L); }
    public static boolean isRecording(Context context) { return startedAt(context) > 0 && stoppedAt(context) == 0; }
    public static boolean hasCompletedSession(Context context) {
        return startedAt(context) > 0 && stoppedAt(context) >= startedAt(context);
    }

    public static void resetWindow(Context context) {
        preferences(context).edit().remove(START).remove(STOP).remove(FAILURE).apply();
    }
}
