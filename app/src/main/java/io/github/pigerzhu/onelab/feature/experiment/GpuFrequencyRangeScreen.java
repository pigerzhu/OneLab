package io.github.pigerzhu.onelab.feature.experiment;

import io.github.pigerzhu.onelab.R;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_GPU_RANGE_EXPERIMENT;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_SDHMS_PERF_CAP_BYPASS;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_SDHMS_THERMAL;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_GPU_RANGE_MAX_MHZ;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_GPU_RANGE_MIN_MHZ;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_GPU_RANGE_RUNTIME_STATUS;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.SDHMS_GPU_FREQS_MHZ;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.RangeSlider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.contract.GpuFrequencyRange;
import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.Ui;

/** Experimental persistent GPU minimum/maximum DVFS votes. */
public final class GpuFrequencyRangeScreen {
    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;

    public GpuFrequencyRangeScreen(MainActivity host, Ui ui, SettingsStore settings) {
        this.host = host;
        this.ui = ui;
        this.settings = settings;
    }

    public View card() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);

        MaterialSwitch toggle = new MaterialSwitch(host);
        body.addView(ui.switchRow(
                host.getString(R.string.gpu_range_title),
                host.getString(R.string.gpu_range_summary),
                toggle));

        GpuFrequencyRange initial = currentRange();
        TextView rangeValue = ui.text(rangeText(initial), 18, true, ui.colorOnSurface);
        body.addView(rangeValue, ui.matchWrap());

        RangeSlider rangeSlider = new RangeSlider(host);
        rangeSlider.setValueFrom(0f);
        rangeSlider.setValueTo(SDHMS_GPU_FREQS_MHZ.length - 1);
        rangeSlider.setStepSize(1f);
        rangeSlider.setValues((float) indexOf(initial.minMhz()),
                (float) indexOf(initial.maxMhz()));
        rangeSlider.setLabelFormatter(value -> frequencyAt(value) + "MHz");
        rangeSlider.addOnChangeListener((slider, value, fromUser) ->
                rangeValue.setText(rangeText(rangeFrom(slider.getValues()))));
        rangeSlider.addOnSliderTouchListener(new RangeSlider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(RangeSlider slider) {
            }

            @Override
            public void onStopTrackingTouch(RangeSlider slider) {
                saveRange(rangeFrom(slider.getValues()));
            }
        });
        body.addView(rangeSlider, ui.matchWrap());

        TextView status = ui.text(statusText(), 14, false, ui.colorOnSurfaceVariant);
        body.addView(status, ui.matchWrap());

        boolean initiallyEnabled = settings.getGlobalInt(KEY_ENABLE_GPU_RANGE_EXPERIMENT, 0) == 1;
        toggle.setChecked(initiallyEnabled);
        rangeSlider.setEnabled(initiallyEnabled);
        boolean[] syncingToggle = {false};
        toggle.setOnCheckedChangeListener((button, enabled) -> {
            if (syncingToggle[0]) return;
            boolean saved = enabled ? enableRangeControl() : disableRangeControl();
            if (!saved) {
                syncingToggle[0] = true;
                button.setChecked(!enabled);
                syncingToggle[0] = false;
                return;
            }
            rangeSlider.setEnabled(enabled);
            status.setText(enabled ? R.string.gpu_range_pending : R.string.gpu_range_disabled);
        });
        return card;
    }

    private boolean enableRangeControl() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(KEY_ENABLE_SDHMS_THERMAL, "1");
        values.put(KEY_ENABLE_SDHMS_PERF_CAP_BYPASS, "1");
        values.put(KEY_ENABLE_GPU_RANGE_EXPERIMENT, "1");
        boolean saved = settings.putGlobalsQuietly(values);
        if (!saved) {
            Toast.makeText(host, R.string.toast_save_failed_permission,
                    Toast.LENGTH_LONG).show();
        }
        return saved;
    }

    private boolean disableRangeControl() {
        boolean saved = settings.putGlobalQuietly(KEY_ENABLE_GPU_RANGE_EXPERIMENT, "0");
        if (!saved) {
            Toast.makeText(host, R.string.toast_save_failed_permission,
                    Toast.LENGTH_LONG).show();
        }
        return saved;
    }

    private GpuFrequencyRange currentRange() {
        return GpuFrequencyRange.normalize(
                settings.getGlobalInt(KEY_GPU_RANGE_MIN_MHZ, 80),
                settings.getGlobalInt(KEY_GPU_RANGE_MAX_MHZ, 1000));
    }

    private void saveRange(GpuFrequencyRange range) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(KEY_GPU_RANGE_MIN_MHZ, String.valueOf(range.minMhz()));
        values.put(KEY_GPU_RANGE_MAX_MHZ, String.valueOf(range.maxMhz()));
        if (!settings.putGlobalsQuietly(values)) {
            Toast.makeText(host, R.string.gpu_range_save_failed, Toast.LENGTH_LONG).show();
        }
    }

    private String statusText() {
        String status = settings.getGlobal(KEY_GPU_RANGE_RUNTIME_STATUS, "unavailable");
        if ("active".equals(status)) return host.getString(R.string.gpu_range_active);
        if ("disabled".equals(status)) return host.getString(R.string.gpu_range_disabled);
        return host.getString(R.string.gpu_range_unavailable);
    }

    private static GpuFrequencyRange rangeFrom(List<Float> values) {
        return GpuFrequencyRange.normalize(frequencyAt(values.get(0)), frequencyAt(values.get(1)));
    }

    private String rangeText(GpuFrequencyRange range) {
        if (range.isLocked()) {
            return host.getString(R.string.gpu_range_locked, range.minMhz());
        }
        return host.getString(R.string.gpu_range_value, range.minMhz(), range.maxMhz());
    }

    private static int frequencyAt(float value) {
        int index = Math.max(0, Math.min(SDHMS_GPU_FREQS_MHZ.length - 1, Math.round(value)));
        return SDHMS_GPU_FREQS_MHZ[index];
    }

    private static int indexOf(int frequency) {
        for (int i = 0; i < SDHMS_GPU_FREQS_MHZ.length; i++) {
            if (SDHMS_GPU_FREQS_MHZ[i] == frequency) return i;
        }
        return 0;
    }
}
