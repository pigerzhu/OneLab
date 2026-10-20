package io.github.pigerzhu.onelab.feature.diagnostics;

import io.github.pigerzhu.onelab.R;

import io.github.pigerzhu.onelab.MainActivity;

import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textview.MaterialTextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.pigerzhu.onelab.diagnostics.DiagnosticReport;
import io.github.pigerzhu.onelab.diagnostics.DiagnosticSession;
import io.github.pigerzhu.onelab.ui.Ui;

public final class DiagnosticsScreen {
    private final MainActivity host;
    private final Ui ui;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean busy;

    public DiagnosticsScreen(MainActivity host, Ui ui) {
        this.host = host;
        this.ui = ui;
    }

    public MaterialCardView card() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);
        body.addView(ui.text(host.getString(R.string.diagnostics_title), 20, true,
                ui.colorOnSurface));
        body.addView(ui.text(
                host.getString(R.string.diagnostics_summary),
                14, false, ui.colorOnSurfaceVariant));
        ui.addSpace(body, 12);

        MaterialTextView status = ui.text(statusText(), 13, false, ui.colorOnSurfaceVariant);
        body.addView(status);
        ui.addSpace(body, 10);

        MaterialButton start = ui.actionButton(host.getString(R.string.diagnostics_start));
        MaterialButton stop = ui.actionButton(host.getString(R.string.diagnostics_stop));
        MaterialButton generate =
                ui.actionButton(host.getString(R.string.diagnostics_generate));
        MaterialButton clear = ui.actionButton(host.getString(R.string.action_clear));
        styleSecondaryAction(start);
        styleSecondaryAction(stop);
        styleSecondaryAction(clear);
        body.addView(generate, ui.matchWrap());
        ui.addSpace(body, 6);

        LinearLayout secondaryActions = new LinearLayout(host);
        secondaryActions.setOrientation(LinearLayout.HORIZONTAL);
        secondaryActions.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(secondaryActions, ui.matchWrap());
        secondaryActions.addView(start, weightedButtonParams(true));
        secondaryActions.addView(stop, weightedButtonParams(true));
        secondaryActions.addView(clear, weightedButtonParams(false));

        start.setOnClickListener(v -> {
            runSessionAction(() -> DiagnosticSession.start(host), R.string.diagnostics_started,
                    status, start, stop, generate, clear);
        });
        stop.setOnClickListener(v -> {
            runSessionAction(() -> DiagnosticSession.stop(host), R.string.diagnostics_stopped,
                    status, start, stop, generate, clear);
        });
        generate.setOnClickListener(v -> {
            setBusy(generate, secondaryActions, false);
            status.setText(R.string.diagnostics_generating);
            executor.execute(() -> {
                try {
                    DiagnosticReport.PublishedReport report =
                            DiagnosticReport.generate(host);
                    host.runOnUiThread(() -> {
                        setBusy(generate, secondaryActions, true);
                        status.setText(host.getString(R.string.diagnostics_generated, report.fileName));
                        syncState(status, start, stop, generate);
                        Toast.makeText(
                                host,
                                host.getString(R.string.diagnostics_saved_to,
                                        report.displayPath),
                                Toast.LENGTH_LONG).show();
                        share(report);
                    });
                } catch (Exception error) {
                    host.runOnUiThread(() -> {
                        setBusy(generate, secondaryActions, true);
                        syncState(status, start, stop, generate);
                        status.setText(host.getString(R.string.diagnostics_generate_failed,
                                error.getClass().getSimpleName()));
                        Toast.makeText(host, R.string.diagnostics_generate_failed_toast,
                                Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
        clear.setOnClickListener(v -> {
            runSessionAction(() -> DiagnosticReport.clear(host), R.string.diagnostics_cleared,
                    status, start, stop, generate, clear);
        });
        SharedPreferences.OnSharedPreferenceChangeListener sessionListener = (prefs, key) ->
                host.runOnUiThread(() -> { if (!busy) syncState(status, start, stop, generate); });
        card.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                DiagnosticSession.preferences(host).registerOnSharedPreferenceChangeListener(sessionListener);
                if (!busy) syncState(status, start, stop, generate);
            }
            @Override public void onViewDetachedFromWindow(View view) {
                DiagnosticSession.preferences(host).unregisterOnSharedPreferenceChangeListener(sessionListener);
            }
        });
        syncState(status, start, stop, generate);
        return card;
    }

    public void onDestroy() {
        executor.shutdown();
    }

    private void runSessionAction(Runnable action, int successMessage, MaterialTextView status,
            MaterialButton start, MaterialButton stop, MaterialButton generate, MaterialButton clear) {
        start.setEnabled(false);
        stop.setEnabled(false);
        generate.setEnabled(false);
        clear.setEnabled(false);
        busy = true;
        executor.execute(() -> {
            try {
                action.run();
                host.runOnUiThread(() -> {
                    busy = false;
                    clear.setEnabled(true);
                    syncState(status, start, stop, generate);
                    Toast.makeText(host, successMessage, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                host.runOnUiThread(() -> {
                    busy = false;
                    clear.setEnabled(true);
                    syncState(status, start, stop, generate);
                    status.setText(host.getString(R.string.diagnostics_record_failed, error.getClass().getSimpleName()));
                });
            }
        });
    }

    private void share(DiagnosticReport.PublishedReport report) {
        Uri uri = report.uri;
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("application/zip")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT,
                        host.getString(R.string.diagnostics_report_subject))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri(
                host.getString(R.string.diagnostics_report_subject), uri));
        host.startActivity(Intent.createChooser(
                intent, host.getString(R.string.diagnostics_share_chooser)));
    }

    private String statusText() {
        if (DiagnosticSession.isRecording(host)) {
            return host.getString(R.string.diagnostics_status_recording);
        }
        if (DiagnosticSession.failure(host) != null) return host.getString(R.string.diagnostics_status_failed);
        if (DiagnosticSession.hasCompletedSession(host)) {
            return host.getString(R.string.diagnostics_status_stopped);
        }
        String latest = DiagnosticReport.latestReportName(host);
        if (latest != null) {
            return host.getString(R.string.diagnostics_status_existing, latest);
        }
        return host.getString(R.string.diagnostics_status_idle);
    }

    private void syncState(
            MaterialTextView status,
            MaterialButton start,
            MaterialButton stop,
            MaterialButton generate) {
        boolean recording = DiagnosticSession.isRecording(host);
        boolean completed = DiagnosticSession.hasCompletedSession(host);
        status.setText(statusText());
        start.setEnabled(!recording);
        stop.setEnabled(recording);
        generate.setEnabled(completed);
    }

    private void styleSecondaryAction(MaterialButton button) {
        button.setSingleLine(true);
        button.setMaxLines(1);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setTextSize(12);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPaddingRelative(ui.dp(6), 0, ui.dp(6), 0);
        button.setCornerRadius(ui.dp(16));
    }

    private LinearLayout.LayoutParams weightedButtonParams(boolean gapAfter) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ui.dp(48), 1);
        if (gapAfter) params.setMarginEnd(ui.dp(4));
        return params;
    }

    private void setBusy(
            MaterialButton primary, LinearLayout actions, boolean enabled) {
        busy = !enabled;
        primary.setEnabled(enabled);
        for (int index = 0; index < actions.getChildCount(); index++) {
            actions.getChildAt(index).setEnabled(enabled);
        }
    }
}
