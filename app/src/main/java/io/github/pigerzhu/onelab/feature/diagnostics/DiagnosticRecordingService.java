package io.github.pigerzhu.onelab.feature.diagnostics;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.R;
import io.github.pigerzhu.onelab.diagnostics.DiagnosticSession;
import io.github.pigerzhu.onelab.system.DiagnosticCapture;

/** Keeps an explicitly requested recording alive while SystemUI/Launcher restarts. */
public final class DiagnosticRecordingService extends Service {
    private static final String CHANNEL = "diagnostic_recording";

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                getString(R.string.diagnostics_title), NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(4101, new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_info)
                .setContentTitle(getString(R.string.diagnostics_title))
                .setContentText(getString(R.string.diagnostics_status_recording))
                .setContentIntent(open).setOngoing(true).build());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!DiagnosticSession.isRecording(this)) { stopSelf(); return START_NOT_STICKY; }
        try {
            DiagnosticCapture.start(this, reason -> {
                DiagnosticSession.failed(this, reason);
                stopSelf();
            });
        } catch (Exception error) {
            DiagnosticSession.failed(this, error.toString());
            stopSelf();
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        DiagnosticCapture.requestStop();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
