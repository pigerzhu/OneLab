package io.github.pigerzhu.onelab.feature.performance;

import io.github.pigerzhu.onelab.R;

import io.github.pigerzhu.onelab.MainActivity;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_DISABLE_SDHMS_BRIGHTNESS_LIMIT;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_DISABLE_SDHMS_CP_THERMAL_MITIGATION;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_DISABLE_SSRM_MULTIWINDOW_LIMIT;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_SDHMS_CPU_CAP_RELEASE;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_SDHMS_PERF_CAP_BYPASS;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_GPU_RANGE_EXPERIMENT;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_SDHMS_THERMAL;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import io.github.pigerzhu.onelab.system.SdhmsClient;
import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.Ui;

public final class ThermalScreen {

    private static final String KEY_LAST_SDHMS_THERMAL_DELTA =
            "onelab_last_sdhms_thermal_delta";
    private static final int SDHMS_SET_THERMAL_DELTA = 28;
    private static final int SDHMS_GET_THERMAL_DELTA = 30;
    private static final int SDHMS_GET_SUPPORTED_THERMAL_DELTA = 31;
    private static final int SDHMS_SET_THERMAL_CONTROL_FLAG = 38;
    private static final int SDHMS_GET_THERMAL_CONTROL_FLAG = 39;
    private static final int THERMAL_GUARDIAN_BASE_CELSIUS = 38;
    private static final int THERMAL_DELTA_MIN = -5;
    private static final int THERMAL_DELTA_MAX = 6;
    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;

    private MaterialSwitch sdhmsThermalSwitch;
    private MaterialSwitch sdhmsBrightnessLimitSwitch;
    private MaterialSwitch sdhmsCpMitigationSwitch;
    private MaterialSwitch sdhmsPerfCapBypassSwitch;
    private MaterialSwitch sdhmsCpuCapReleaseSwitch;
    private MaterialSwitch sdhmsMultiWindowThermalSwitch;
    private MaterialSwitch thermalCpuSwitch;
    private MaterialSwitch thermalBrightnessSwitch;
    private MaterialSwitch thermalHrrSwitch;
    private MaterialSwitch thermalNetworkSwitch;
    private TextView sdhmsHiddenThermalStatus;
    private TextView thermalDeltaValueLabel;
    private Slider thermalDeltaSlider;

    public ThermalScreen(MainActivity host, Ui ui, SettingsStore settings) {
        this.host = host;
        this.ui = ui;
        this.settings = settings;
    }

    /** Entry card for the Experiments page. Tapping opens {@link #showPage()}. */
    public View entryCard() {
        MaterialCardView card = ui.card();
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> showPage());

        LinearLayout body = ui.cardBody();
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(body);

        body.addView(ui.text(host.getString(R.string.thermal_entry_title), 20, true,
                        ui.colorOnSurface),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView arrow = ui.text("›", 28, false, ui.colorOnSurfaceVariant);
        arrow.setGravity(Gravity.CENTER);
        body.addView(arrow, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(40)));
        return card;
    }

    /** Navigate to the SDHMS/ThermalGuardian sub-page. */
    void showPage() {
        host.setNestedBackAction(() -> host.showExperimentsPage(true));
        LinearLayout root = host.beginSubPage(
                host.getString(R.string.thermal_page_title),
                host.getString(R.string.thermal_page_summary),
                1);
        root.addView(sdhmsThermalMasterCard());
        root.addView(customThermalDeltaCard());
        root.addView(sdhmsExperimentalThermalCard());
    }

    /** Master-enable card. Also shown on the Performance page via host. */
    public View sdhmsThermalMasterCard() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);

        sdhmsThermalSwitch = new MaterialSwitch(host);
        body.addView(ui.switchRow(
                host.getString(R.string.thermal_master_title),
                host.getString(R.string.thermal_master_summary),
                sdhmsThermalSwitch,
                20));
        sdhmsThermalSwitch.setChecked(isSdhmsThermalEnabled());
        sdhmsThermalSwitch.setOnCheckedChangeListener((button, enabled) -> setSdhmsThermalEnabled(enabled));
        return card;
    }

    /** SIOP perf-cap bypass card. Also shown on the Performance page via host. */
    public View sdhmsHiddenThermalCard() {
        sdhmsBrightnessLimitSwitch = null;
        sdhmsCpMitigationSwitch = null;
        sdhmsMultiWindowThermalSwitch = null;
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);

        body.addView(ui.text(host.getString(R.string.thermal_siop_title), 20, true,
                ui.colorOnSurface));

        ui.addSpace(body, 14);
        sdhmsPerfCapBypassSwitch = new MaterialSwitch(host);
        body.addView(ui.switchRow(
                host.getString(R.string.thermal_siop_bypass),
                host.getString(R.string.thermal_siop_bypass_summary),
                sdhmsPerfCapBypassSwitch));
        sdhmsPerfCapBypassSwitch.setChecked(isSdhmsPerfCapBypassEnabled());
        sdhmsPerfCapBypassSwitch.setOnCheckedChangeListener((button, enabled) ->
                setSdhmsHiddenThermalSwitch(KEY_ENABLE_SDHMS_PERF_CAP_BYPASS, enabled));

        sdhmsCpuCapReleaseSwitch = new MaterialSwitch(host);
        body.addView(ui.switchRow(
                host.getString(R.string.thermal_cpu_release),
                host.getString(R.string.thermal_cpu_release_summary),
                sdhmsCpuCapReleaseSwitch));
        sdhmsCpuCapReleaseSwitch.setChecked(isSdhmsCpuCapReleaseEnabled());
        sdhmsCpuCapReleaseSwitch.setOnCheckedChangeListener((button, enabled) ->
                setSdhmsHiddenThermalSwitch(KEY_ENABLE_SDHMS_CPU_CAP_RELEASE, enabled));

        ui.addSpace(body, 12);
        sdhmsHiddenThermalStatus = ui.text("", 14, false, ui.colorOnSurfaceVariant);
        body.addView(sdhmsHiddenThermalStatus);
        updateSdhmsHiddenThermalStatus();
        return card;
    }

    // Legacy card kept for completeness; not currently wired to any page.
    private View thermalGuardianControlCard() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);

        LinearLayout header = new LinearLayout(host);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(header, ui.matchWrap());

        LinearLayout copy = new LinearLayout(host);
        copy.setOrientation(LinearLayout.VERTICAL);
        header.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        copy.addView(ui.text(host.getString(R.string.thermal_experimental_title), 20, true,
                ui.colorOnSurface));
        copy.addView(ui.text(
                host.getString(R.string.thermal_experimental_summary),
                14, false, ui.colorOnSurfaceVariant));

        sdhmsThermalSwitch = new MaterialSwitch(host);
        sdhmsThermalSwitch.setChecked(isSdhmsThermalEnabled());
        sdhmsThermalSwitch.setOnCheckedChangeListener((button, enabled) -> setSdhmsThermalEnabled(enabled));
        header.addView(sdhmsThermalSwitch);

        ui.addSpace(body, 16);
        thermalDeltaValueLabel = ui.text("", 28, true, ui.colorOnSurface);
        thermalDeltaValueLabel.setGravity(Gravity.CENTER);
        body.addView(thermalDeltaValueLabel, ui.matchWrap());

        thermalDeltaSlider = new Slider(host);
        thermalDeltaSlider.setValueFrom(-2f);
        thermalDeltaSlider.setValueTo(2f);
        thermalDeltaSlider.setStepSize(1f);
        thermalDeltaSlider.setLabelFormatter(value -> thermalDeltaText(Math.round(value)));
        thermalDeltaSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                updateThermalDeltaValueLabel(Math.round(value));
            }
        });
        thermalDeltaSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                writeThermalDelta(Math.round(slider.getValue()));
            }
        });
        body.addView(thermalDeltaSlider, ui.matchWrap());

        ui.addSpace(body, 14);
        body.addView(ui.text(host.getString(R.string.thermal_extra_limits), 14, true,
                ui.colorOnSurfaceVariant));
        thermalCpuSwitch = thermalFlagSwitch(
                body, host.getString(R.string.thermal_limit_cpu), 1);
        thermalBrightnessSwitch = thermalFlagSwitch(
                body, host.getString(R.string.thermal_limit_brightness), 2);
        thermalHrrSwitch = thermalFlagSwitch(
                body, host.getString(R.string.thermal_limit_refresh_rate), 4);
        thermalNetworkSwitch = thermalFlagSwitch(
                body, host.getString(R.string.thermal_limit_network), 8);

        ui.addSpace(body, 12);
        MaterialButton refreshButton = ui.actionButton(
                host.getString(R.string.action_refresh_status));
        refreshButton.setOnClickListener(v -> updateThermalGuardianStatus());
        body.addView(refreshButton,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(46)));

        updateThermalGuardianStatus();
        return card;
    }

    private View customThermalDeltaCard() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);

        body.addView(ui.text(host.getString(R.string.thermal_delta_title), 20, true,
                ui.colorOnSurface));
        body.addView(ui.text(
                host.getString(R.string.thermal_delta_summary),
                14, false, ui.colorOnSurfaceVariant));

        ui.addSpace(body, 14);
        thermalDeltaValueLabel = ui.text("", 28, true, ui.colorOnSurface);
        thermalDeltaValueLabel.setGravity(Gravity.CENTER);
        body.addView(thermalDeltaValueLabel, ui.matchWrap());

        thermalDeltaSlider = new Slider(host);
        thermalDeltaSlider.setValueFrom(THERMAL_DELTA_MIN);
        thermalDeltaSlider.setValueTo(THERMAL_DELTA_MAX);
        thermalDeltaSlider.setStepSize(1f);
        thermalDeltaSlider.setLabelFormatter(value -> thermalDeltaText(Math.round(value)));
        thermalDeltaSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                updateThermalDeltaValueLabel(Math.round(value));
            }
        });
        thermalDeltaSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                writeThermalDelta(Math.round(slider.getValue()));
            }
        });
        body.addView(thermalDeltaSlider, ui.matchWrap());

        ui.addSpace(body, 12);
        MaterialButton resetButton = ui.actionButton(
                host.getString(R.string.action_reset_default));
        resetButton.setOnClickListener(v -> writeThermalDelta(0));
        body.addView(resetButton,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(46)));

        updateThermalGuardianStatus();
        return card;
    }

    private View sdhmsExperimentalThermalCard() {
        sdhmsPerfCapBypassSwitch = null;
        sdhmsCpuCapReleaseSwitch = null;
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);

        body.addView(ui.text(host.getString(R.string.thermal_hidden_title), 20, true,
                ui.colorOnSurface));
        body.addView(ui.text(
                host.getString(R.string.thermal_hidden_summary),
                14, false, ui.colorOnSurfaceVariant));

        ui.addSpace(body, 14);
        sdhmsBrightnessLimitSwitch = new MaterialSwitch(host);
        body.addView(ui.switchRow(
                host.getString(R.string.thermal_brightness_title),
                host.getString(R.string.thermal_brightness_summary),
                sdhmsBrightnessLimitSwitch));
        sdhmsBrightnessLimitSwitch.setChecked(isSdhmsBrightnessLimitDisabled());
        sdhmsBrightnessLimitSwitch.setOnCheckedChangeListener((button, enabled) ->
                setSdhmsHiddenThermalSwitch(KEY_DISABLE_SDHMS_BRIGHTNESS_LIMIT, enabled));

        sdhmsCpMitigationSwitch = new MaterialSwitch(host);
        body.addView(ui.switchRow(
                host.getString(R.string.thermal_modem_title),
                host.getString(R.string.thermal_modem_summary),
                sdhmsCpMitigationSwitch));
        sdhmsCpMitigationSwitch.setChecked(isSdhmsCpMitigationDisabled());
        sdhmsCpMitigationSwitch.setOnCheckedChangeListener((button, enabled) ->
                setSdhmsHiddenThermalSwitch(KEY_DISABLE_SDHMS_CP_THERMAL_MITIGATION, enabled));

        sdhmsMultiWindowThermalSwitch = new MaterialSwitch(host);
        body.addView(ui.switchRow(
                host.getString(R.string.thermal_multiwindow_title),
                host.getString(R.string.thermal_multiwindow_summary),
                sdhmsMultiWindowThermalSwitch));
        sdhmsMultiWindowThermalSwitch.setChecked(isSsrmMultiWindowLimitDisabled());
        sdhmsMultiWindowThermalSwitch.setOnCheckedChangeListener((button, enabled) ->
                setSdhmsHiddenThermalSwitch(KEY_DISABLE_SSRM_MULTIWINDOW_LIMIT, enabled));
        ui.addSpace(body, 10);

        ui.addSpace(body, 12);
        sdhmsHiddenThermalStatus = ui.text("", 14, false, ui.colorOnSurfaceVariant);
        body.addView(sdhmsHiddenThermalStatus);
        updateSdhmsHiddenThermalStatus();
        return card;
    }

    private MaterialSwitch thermalFlagSwitch(LinearLayout parent, String label, int bit) {
        LinearLayout row = new LinearLayout(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(44)));

        row.addView(ui.text(label, 14, false, ui.colorOnSurface),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        MaterialSwitch toggle = new MaterialSwitch(host);
        toggle.setTag(bit);
        toggle.setOnCheckedChangeListener((button, enabled) -> {
            if (ui.syncingUi) {
                return;
            }
            writeThermalControlFlag(thermalControlFlagFromUi());
        });
        row.addView(toggle);
        return toggle;
    }

    private void setSdhmsThermalEnabled(boolean enabled) {
        settings.setGlobal(KEY_ENABLE_SDHMS_THERMAL, enabled ? "1" : "0");
        if (!enabled) {
            settings.putGlobalQuietly(KEY_ENABLE_GPU_RANGE_EXPERIMENT, "0");
        }
        updateThermalGuardianStatus();
        syncSdhmsHiddenThermalControls();
    }

    private void setSdhmsHiddenThermalSwitch(String key, boolean enabled) {
        settings.setGlobal(key, enabled ? "1" : "0");
        if (KEY_ENABLE_SDHMS_PERF_CAP_BYPASS.equals(key) && !enabled) {
            settings.putGlobalQuietly(KEY_ENABLE_GPU_RANGE_EXPERIMENT, "0");
        }
        syncSdhmsHiddenThermalControls();
    }

    private void syncSdhmsHiddenThermalControls() {
        int supported = sdhmsGetInt(SDHMS_GET_SUPPORTED_THERMAL_DELTA, Integer.MIN_VALUE);
        updateSdhmsHiddenThermalStatus();
        Toast.makeText(host,
                supported == Integer.MIN_VALUE
                        ? R.string.toast_saved_reboot_required : R.string.toast_applied,
                Toast.LENGTH_SHORT).show();
    }

    private void updateSdhmsHiddenThermalStatus() {
        if (sdhmsHiddenThermalStatus == null) {
            return;
        }
        boolean hookEnabled = isSdhmsThermalEnabled();
        boolean brightnessDisabled = isSdhmsBrightnessLimitDisabled();
        boolean cpDisabled = isSdhmsCpMitigationDisabled();
        boolean perfCapBypassEnabled = isSdhmsPerfCapBypassEnabled();
        boolean cpuCapReleaseEnabled = isSdhmsCpuCapReleaseEnabled();
        boolean ssrmMultiWindowLimitDisabled = isSsrmMultiWindowLimitDisabled();
        if (sdhmsThermalSwitch != null && sdhmsThermalSwitch.isChecked() != hookEnabled) {
            sdhmsThermalSwitch.setOnCheckedChangeListener(null);
            sdhmsThermalSwitch.setChecked(hookEnabled);
            sdhmsThermalSwitch.setOnCheckedChangeListener(
                    (button, enabled) -> setSdhmsThermalEnabled(enabled));
        }
        updateHiddenSwitch(sdhmsBrightnessLimitSwitch, brightnessDisabled, hookEnabled,
                (button, enabled) ->
                        setSdhmsHiddenThermalSwitch(KEY_DISABLE_SDHMS_BRIGHTNESS_LIMIT, enabled));
        updateHiddenSwitch(sdhmsCpMitigationSwitch, cpDisabled, hookEnabled,
                (button, enabled) ->
                        setSdhmsHiddenThermalSwitch(KEY_DISABLE_SDHMS_CP_THERMAL_MITIGATION, enabled));
        updateHiddenSwitch(sdhmsMultiWindowThermalSwitch, ssrmMultiWindowLimitDisabled, true,
                (button, enabled) ->
                        setSdhmsHiddenThermalSwitch(KEY_DISABLE_SSRM_MULTIWINDOW_LIMIT, enabled));
        updateHiddenSwitch(sdhmsPerfCapBypassSwitch, perfCapBypassEnabled, hookEnabled,
                (button, enabled) ->
                        setSdhmsHiddenThermalSwitch(KEY_ENABLE_SDHMS_PERF_CAP_BYPASS, enabled));
        updateHiddenSwitch(sdhmsCpuCapReleaseSwitch, cpuCapReleaseEnabled,
                hookEnabled && perfCapBypassEnabled,
                (button, enabled) ->
                        setSdhmsHiddenThermalSwitch(KEY_ENABLE_SDHMS_CPU_CAP_RELEASE, enabled));
        StringBuilder status = new StringBuilder();
        if (!hookEnabled) {
            appendStatusLine(status, host.getString(R.string.thermal_hidden_needs_master));
        }
        sdhmsHiddenThermalStatus.setText(status.toString());
        sdhmsHiddenThermalStatus.setVisibility(status.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private void appendStatusLine(StringBuilder status, String line) {
        if (status.length() > 0) {
            status.append('\n');
        }
        status.append(line);
    }

    private void updateHiddenSwitch(MaterialSwitch toggle, boolean checked, boolean enabled,
                                    CompoundButton.OnCheckedChangeListener listener) {
        if (toggle == null) {
            return;
        }
        if (toggle.isChecked() != checked) {
            toggle.setOnCheckedChangeListener(null);
            toggle.setChecked(checked);
            toggle.setOnCheckedChangeListener(listener);
        }
        toggle.setEnabled(enabled);
    }

    private void updateThermalGuardianStatus() {
        boolean enabled = isSdhmsThermalEnabled();
        int delta = sdhmsGetInt(SDHMS_GET_THERMAL_DELTA, Integer.MIN_VALUE);
        int flag = sdhmsGetInt(SDHMS_GET_THERMAL_CONTROL_FLAG, Integer.MIN_VALUE);

        if (sdhmsThermalSwitch != null && sdhmsThermalSwitch.isChecked() != enabled) {
            sdhmsThermalSwitch.setOnCheckedChangeListener(null);
            sdhmsThermalSwitch.setChecked(enabled);
            sdhmsThermalSwitch.setOnCheckedChangeListener(
                    (button, isEnabled) -> setSdhmsThermalEnabled(isEnabled));
        }

        int lastDelta = settings.getGlobalInt(KEY_LAST_SDHMS_THERMAL_DELTA, 0);
        int displayDelta = delta >= THERMAL_DELTA_MIN && delta <= THERMAL_DELTA_MAX
                ? delta
                : Math.max(THERMAL_DELTA_MIN, Math.min(THERMAL_DELTA_MAX, lastDelta));
        if (thermalDeltaSlider != null) {
            thermalDeltaSlider.setValue(displayDelta);
            thermalDeltaSlider.setEnabled(enabled);
        }
        updateThermalDeltaValueLabel(displayDelta);
        updateThermalFlagSwitches(flag >= 0 ? flag : 0, enabled);

    }

    private void writeThermalDelta(int delta) {
        if (!isSdhmsThermalEnabled()) {
            Toast.makeText(host, R.string.thermal_enable_first, Toast.LENGTH_SHORT).show();
            updateThermalGuardianStatus();
            return;
        }
        boolean ok = sdhmsSetInt(SDHMS_SET_THERMAL_DELTA,
                Math.max(THERMAL_DELTA_MIN, Math.min(THERMAL_DELTA_MAX, delta)));
        if (ok) {
            int clamped = Math.max(THERMAL_DELTA_MIN, Math.min(THERMAL_DELTA_MAX, delta));
            settings.setGlobal(KEY_LAST_SDHMS_THERMAL_DELTA, String.valueOf(clamped));
            if (thermalDeltaSlider != null) {
                thermalDeltaSlider.setValue(clamped);
            }
            updateThermalDeltaValueLabel(clamped);
        }
        Toast.makeText(host,
                ok ? R.string.thermal_delta_written : R.string.thermal_write_rejected,
                ok ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
        updateThermalGuardianStatus();
    }

    private void writeThermalControlFlag(int flag) {
        if (!isSdhmsThermalEnabled()) {
            Toast.makeText(host, R.string.thermal_enable_first, Toast.LENGTH_SHORT).show();
            updateThermalGuardianStatus();
            return;
        }
        boolean ok = sdhmsSetInt(SDHMS_SET_THERMAL_CONTROL_FLAG, flag);
        Toast.makeText(host,
                ok ? R.string.thermal_flags_written : R.string.thermal_flag_write_rejected,
                ok ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
        updateThermalGuardianStatus();
    }

    private int thermalControlFlagFromUi() {
        int flag = 0;
        flag |= thermalFlagBit(thermalCpuSwitch);
        flag |= thermalFlagBit(thermalBrightnessSwitch);
        flag |= thermalFlagBit(thermalHrrSwitch);
        flag |= thermalFlagBit(thermalNetworkSwitch);
        return flag;
    }

    private int thermalFlagBit(MaterialSwitch toggle) {
        if (toggle == null || !toggle.isChecked()) {
            return 0;
        }
        Object tag = toggle.getTag();
        return tag instanceof Integer ? (Integer) tag : 0;
    }

    private void updateThermalFlagSwitches(int flag, boolean enabled) {
        ui.syncingUi = true;
        try {
            setThermalFlagSwitch(thermalCpuSwitch, flag, enabled);
            setThermalFlagSwitch(thermalBrightnessSwitch, flag, enabled);
            setThermalFlagSwitch(thermalHrrSwitch, flag, enabled);
            setThermalFlagSwitch(thermalNetworkSwitch, flag, enabled);
        } finally {
            ui.syncingUi = false;
        }
    }

    private void setThermalFlagSwitch(MaterialSwitch toggle, int flag, boolean enabled) {
        if (toggle == null) {
            return;
        }
        Object tag = toggle.getTag();
        int bit = tag instanceof Integer ? (Integer) tag : 0;
        toggle.setChecked((flag & bit) != 0);
        toggle.setEnabled(enabled);
    }

    private void updateThermalDeltaValueLabel(int delta) {
        if (thermalDeltaValueLabel != null) {
            thermalDeltaValueLabel.setText(thermalDeltaText(delta));
        }
    }

    private String thermalDeltaText(int delta) {
        if (delta < THERMAL_DELTA_MIN || delta > THERMAL_DELTA_MAX) {
            return String.valueOf(delta);
        }
        String sign = delta > 0 ? "+" : "";
        return sign + delta + "°C";
    }

    private String thermalDeltaTargetText(int delta) {
        return (THERMAL_GUARDIAN_BASE_CELSIUS + delta) + "°C";
    }

    private int sdhmsGetInt(int transactionCode, int fallback) {
        return SdhmsClient.getInt(transactionCode, fallback);
    }

    private boolean sdhmsSetInt(int transactionCode, int value) {
        return SdhmsClient.setInt(transactionCode, value);
    }

    private boolean isSdhmsThermalEnabled() {
        return "1".equals(settings.getGlobal(KEY_ENABLE_SDHMS_THERMAL, "0"));
    }

    private boolean isSdhmsBrightnessLimitDisabled() {
        return "1".equals(settings.getGlobal(KEY_DISABLE_SDHMS_BRIGHTNESS_LIMIT, "0"));
    }

    private boolean isSdhmsCpMitigationDisabled() {
        return "1".equals(settings.getGlobal(KEY_DISABLE_SDHMS_CP_THERMAL_MITIGATION, "0"));
    }

    private boolean isSdhmsPerfCapBypassEnabled() {
        return "1".equals(settings.getGlobal(KEY_ENABLE_SDHMS_PERF_CAP_BYPASS, "0"));
    }

    private boolean isSdhmsCpuCapReleaseEnabled() {
        return "1".equals(settings.getGlobal(KEY_ENABLE_SDHMS_CPU_CAP_RELEASE, "1"));
    }

    private boolean isSsrmMultiWindowLimitDisabled() {
        return "1".equals(settings.getGlobal(KEY_DISABLE_SSRM_MULTIWINDOW_LIMIT, "0"));
    }

}
