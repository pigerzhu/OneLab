package io.github.pigerzhu.onelab.feature.window;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.pigerzhu.onelab.system.DeviceStateClient;
import io.github.pigerzhu.onelab.ui.ChoiceGroup;
import io.github.pigerzhu.onelab.ui.Ui;

public final class CoverScreen {
    private static final int REQ_PICK_COVER_IMAGE = 4201;
    private static final String PREF_OUTER_SYSTEM_ENABLED = "outer_system_enabled";
    private static final String PREF_OUTER_SYSTEM_BOOT_COUNT = "outer_system_boot_count";

    private final MainActivity host;
    private final Ui ui;
    private final DeviceStateClient deviceState = new DeviceStateClient();
    private final ExecutorService deviceStateExecutor = Executors.newSingleThreadExecutor();
    private CoverDisplayPresenter coverPresenter;

    public CoverScreen(MainActivity host, Ui ui) {
        this.host = host;
        this.ui = ui;
    }

    public View outerSystemCard() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);

        TextView status = ui.text("", 14, false, ui.colorOnSurfaceVariant);
        status.setVisibility(View.GONE);
        MaterialSwitch toggle = new MaterialSwitch(host);
        toggle.setChecked(cachedOuterSystemEnabled());

        LinearLayout header = new LinearLayout(host);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(ui.text(host.getString(R.string.cover_outer_system_title), 20, true,
                        ui.colorOnSurface),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(toggle);
        body.addView(header, ui.matchWrap());

        body.addView(status);

        toggle.setOnCheckedChangeListener((button, enabled) -> {
            setOuterSystemState(toggle, status, enabled);
        });
        return card;
    }

    private void setOuterSystemState(MaterialSwitch toggle, TextView status, boolean enabled) {
        toggle.setEnabled(false);
        status.setVisibility(View.VISIBLE);
        status.setText(host.getString(enabled
                ? R.string.cover_outer_system_switching
                : R.string.cover_outer_system_restoring));
        deviceStateExecutor.execute(() -> {
            boolean supported = deviceState.supportsOuterDefault();
            int state = supported
                    ? deviceState.setOuterDefault(enabled)
                    : DeviceStateClient.STATE_UNKNOWN;
            boolean success = enabled
                    ? state == DeviceStateClient.STATE_OUTER_DEFAULT
                    : state != DeviceStateClient.STATE_UNKNOWN
                    && state != DeviceStateClient.STATE_OUTER_DEFAULT;
            host.runOnUiThread(() -> {
                if (success) {
                    saveOuterSystemEnabled(enabled);
                    applyOuterSystemState(toggle, status, true, state);
                } else {
                    toggle.setOnCheckedChangeListener(null);
                    toggle.setChecked(!enabled);
                    toggle.setOnCheckedChangeListener((button, checked) ->
                            setOuterSystemState(toggle, status, checked));
                    toggle.setEnabled(true);
                    status.setText(host.getString(supported
                            ? R.string.cover_outer_system_switch_failed
                            : R.string.cover_outer_system_unsupported));
                }
                Toast.makeText(host,
                        host.getString(success
                                ? (enabled ? R.string.cover_outer_system_switched
                                : R.string.cover_outer_system_restored)
                                : (supported ? R.string.cover_outer_system_switch_failed
                                : R.string.cover_outer_system_unsupported)),
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void applyOuterSystemState(MaterialSwitch toggle, TextView status,
                                       boolean supported, int state) {
        toggle.setOnCheckedChangeListener(null);
        toggle.setChecked(state == DeviceStateClient.STATE_OUTER_DEFAULT);
        toggle.setOnCheckedChangeListener((button, enabled) -> {
            setOuterSystemState(toggle, status, enabled);
        });
        toggle.setEnabled(supported && state != DeviceStateClient.STATE_UNKNOWN);
        if (!supported) {
            status.setText(R.string.cover_outer_system_state_unavailable);
        } else if (state == DeviceStateClient.STATE_UNKNOWN) {
            status.setText(R.string.cover_outer_system_state_unreadable);
        } else if (state == DeviceStateClient.STATE_OUTER_DEFAULT) {
            status.setText(R.string.cover_outer_system_state_outer);
        } else {
            status.setText(R.string.cover_outer_system_state_inner);
        }
    }

    private boolean cachedOuterSystemEnabled() {
        SharedPreferences prefs = coverPrefs();
        return prefs.getInt(PREF_OUTER_SYSTEM_BOOT_COUNT, -1) == currentBootCount()
                && prefs.getBoolean(PREF_OUTER_SYSTEM_ENABLED, false);
    }

    private void saveOuterSystemEnabled(boolean enabled) {
        coverPrefs().edit()
                .putBoolean(PREF_OUTER_SYSTEM_ENABLED, enabled)
                .putInt(PREF_OUTER_SYSTEM_BOOT_COUNT, currentBootCount())
                .apply();
    }

    private int currentBootCount() {
        return Settings.Global.getInt(
                host.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }

    public View card() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);

        body.addView(ui.text(host.getString(R.string.cover_content_title), 20, true,
                ui.colorOnSurface));

        ui.addSpace(body, 12);
        TextView status = ui.text(host.getString(R.string.cover_content_checking), 14, false,
                ui.colorOnSurfaceVariant);
        body.addView(status);

        if (coverPresenter == null) {
            coverPresenter = new CoverDisplayPresenter(host);
        }

        ui.addSpace(body, 14);
        ChoiceGroup modeGroup = new ChoiceGroup(host, ui);
        body.addView(modeGroup, ui.matchWrap());
        modeGroup.addOption(host.getString(R.string.cover_mode_clock),
                host.getString(R.string.cover_mode_clock_summary),
                CoverDisplayPresenter.MODE_CLOCK);
        modeGroup.addOption(host.getString(R.string.cover_mode_text),
                host.getString(R.string.cover_mode_text_summary),
                CoverDisplayPresenter.MODE_TEXT);
        modeGroup.addOption(host.getString(R.string.cover_mode_image),
                host.getString(R.string.cover_mode_image_summary),
                CoverDisplayPresenter.MODE_IMAGE);

        ui.addSpace(body, 10);
        MaterialButton editButton = new MaterialButton(
                host, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        editButton.setText(R.string.cover_content_edit);
        body.addView(editButton, ui.matchWrap());

        ui.addSpace(body, 14);
        LinearLayout actions = new LinearLayout(host);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(actions, ui.matchWrap());

        MaterialButton startButton = new MaterialButton(host);
        startButton.setText(R.string.cover_content_start);
        startButton.setEnabled(false);
        actions.addView(startButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        MaterialButton stopButton = new MaterialButton(
                host, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        stopButton.setText(R.string.cover_content_stop);
        stopButton.setEnabled(false);
        LinearLayout.LayoutParams stopParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        stopParams.setMarginStart(ui.dp(12));
        actions.addView(stopButton, stopParams);

        // Restore the saved content choice.
        SharedPreferences prefs = coverPrefs();
        int savedMode = prefs.getInt("mode", CoverDisplayPresenter.MODE_CLOCK);
        coverPresenter.setContentMode(savedMode);
        coverPresenter.setCustomText(prefs.getString("text", ""));
        String savedUri = prefs.getString("image_uri", null);
        if (savedUri != null) {
            coverPresenter.setImageUri(Uri.parse(savedUri));
        }
        modeGroup.setValue(savedMode);
        editButton.setEnabled(savedMode != CoverDisplayPresenter.MODE_CLOCK);

        modeGroup.setOnChoiceChangedListener(mode -> {
            coverPrefs().edit().putInt("mode", mode).apply();
            coverPresenter.setContentMode(mode);
            editButton.setEnabled(mode != CoverDisplayPresenter.MODE_CLOCK);
        });
        editButton.setOnClickListener(v -> {
            if (coverPresenter.contentMode() == CoverDisplayPresenter.MODE_IMAGE) {
                pickCoverImage();
            } else {
                showCoverTextDialog();
            }
        });

        coverPresenter.setStatusListener((text, canPresent, active) -> host.runOnUiThread(() -> {
            status.setText(text);
            status.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
            startButton.setEnabled(canPresent);
            stopButton.setEnabled(active);
        }));
        startButton.setOnClickListener(v -> coverPresenter.present());
        stopButton.setOnClickListener(v -> coverPresenter.end());
        coverPresenter.startListening();
        return card;
    }

    private SharedPreferences coverPrefs() {
        return host.getSharedPreferences("onelab_cover", Activity.MODE_PRIVATE);
    }

    private void showCoverTextDialog() {
        EditText input = new EditText(host);
        input.setText(coverPrefs().getString("text", ""));
        input.setSelectAllOnFocus(true);
        FrameLayout container = new FrameLayout(host);
        container.setPadding(ui.dp(22), ui.dp(8), ui.dp(22), 0);
        container.addView(input, ui.matchWrap());
        new AlertDialog.Builder(host)
                .setTitle(R.string.cover_text_dialog_title)
                .setView(container)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                    String value = input.getText().toString();
                    coverPrefs().edit().putString("text", value).apply();
                    if (coverPresenter != null) {
                        coverPresenter.setCustomText(value);
                    }
                })
                .show();
    }

    private void pickCoverImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            host.startActivityForResult(intent, REQ_PICK_COVER_IMAGE);
        } catch (Exception e) {
            Toast.makeText(host, R.string.cover_image_picker_unavailable,
                    Toast.LENGTH_SHORT).show();
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_PICK_COVER_IMAGE || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            host.getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Some providers don't grant persistable access; the URI still works this session.
        }
        coverPrefs().edit().putString("image_uri", uri.toString()).apply();
        if (coverPresenter != null) {
            coverPresenter.setImageUri(uri);
        }
    }

    public void onDestroy() {
        deviceStateExecutor.shutdownNow();
        if (coverPresenter != null) {
            coverPresenter.stopListening();
            coverPresenter.end();
        }
    }
}
