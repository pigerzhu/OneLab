package io.github.pigerzhu.onelab.feature.experiment;

import io.github.pigerzhu.onelab.R;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_GPU_RANGE_EXPERIMENT;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_SDHMS_PERF_CAP_BYPASS;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_SDHMS_THERMAL;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_GPU_RANGE_MAX_MHZ;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_GPU_RANGE_MIN_MHZ;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_GPU_RANGE_RUNTIME_STATUS;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_GPU_SUPPORTED_FREQUENCIES;

import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
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
import io.github.pigerzhu.onelab.contract.GpuFrequencyTable;
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
        populate(card);
        ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                populate(card);
            }
        };
        card.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                host.getContentResolver().registerContentObserver(
                        Settings.Global.getUriFor(KEY_GPU_SUPPORTED_FREQUENCIES),
                        false,
                        observer);
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                host.getContentResolver().unregisterContentObserver(observer);
            }
        });
        return card;
    }

    private void populate(MaterialCardView card) {
        card.removeAllViews();
        LinearLayout body = ui.cardBody();
        card.addView(body);

        MaterialSwitch toggle = new MaterialSwitch(host);
        body.addView(ui.switchRow(
                host.getString(R.string.gpu_range_title),
                host.getString(R.string.gpu_range_summary),
                toggle));

        int bootCount = settings.getGlobalInt(Settings.Global.BOOT_COUNT, -1);
        int[] frequencies = GpuFrequencyTable.parseSnapshot(
                settings.getGlobal(KEY_GPU_SUPPORTED_FREQUENCIES, ""), bootCount);
        boolean frequenciesAvailable = GpuFrequencyTable.isUsable(frequencies);
        GpuFrequencyRange initial = frequenciesAvailable ? currentRange(frequencies) : null;
        TextView rangeValue = ui.text(
                frequenciesAvailable ? rangeText(initial)
                        : host.getString(R.string.gpu_range_unavailable),
                18, true, ui.colorOnSurface);
        body.addView(rangeValue, ui.matchWrap());

        RangeSlider rangeSlider = new RangeSlider(host);
        rangeSlider.setValueFrom(0f);
        rangeSlider.setValueTo(frequenciesAvailable ? frequencies.length - 1 : 1f);
        rangeSlider.setStepSize(1f);
        rangeSlider.setValues(
                frequenciesAvailable ? (float) indexOf(initial.minMhz(), frequencies) : 0f,
                frequenciesAvailable ? (float) indexOf(initial.maxMhz(), frequencies) : 1f);
        if (frequenciesAvailable) {
            rangeSlider.setLabelFormatter(value -> frequencyAt(value, frequencies) + "MHz");
        }
        rangeSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (frequenciesAvailable) {
                rangeValue.setText(rangeText(rangeFrom(slider.getValues(), frequencies)));
            }
        });
        rangeSlider.addOnSliderTouchListener(new RangeSlider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(RangeSlider slider) {
            }

            @Override
            public void onStopTrackingTouch(RangeSlider slider) {
                if (frequenciesAvailable) {
                    saveRange(rangeFrom(slider.getValues(), frequencies));
                }
            }
        });
        body.addView(rangeSlider, ui.matchWrap());

        TextView status = ui.text(statusText(), 14, false, ui.colorOnSurfaceVariant);
        body.addView(status, ui.matchWrap());

        boolean initiallyEnabled = settings.getGlobalInt(KEY_ENABLE_GPU_RANGE_EXPERIMENT, 0) == 1;
        toggle.setChecked(initiallyEnabled);
        toggle.setEnabled(frequenciesAvailable || initiallyEnabled);
        rangeSlider.setEnabled(initiallyEnabled && frequenciesAvailable);
        boolean[] syncingToggle = {false};
        toggle.setOnCheckedChangeListener((button, enabled) -> {
            if (syncingToggle[0]) return;
            if (enabled && !frequenciesAvailable) {
                syncingToggle[0] = true;
                button.setChecked(false);
                syncingToggle[0] = false;
                return;
            }
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

    private GpuFrequencyRange currentRange(int[] frequencies) {
        return GpuFrequencyRange.normalize(
                settings.getGlobalInt(KEY_GPU_RANGE_MIN_MHZ, frequencies[0]),
                settings.getGlobalInt(KEY_GPU_RANGE_MAX_MHZ,
                        frequencies[frequencies.length - 1]),
                frequencies);
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

    private static GpuFrequencyRange rangeFrom(List<Float> values, int[] frequencies) {
        return GpuFrequencyRange.normalize(
                frequencyAt(values.get(0), frequencies),
                frequencyAt(values.get(1), frequencies),
                frequencies);
    }

    private String rangeText(GpuFrequencyRange range) {
        if (range.isLocked()) {
            return host.getString(R.string.gpu_range_locked, range.minMhz());
        }
        return host.getString(R.string.gpu_range_value, range.minMhz(), range.maxMhz());
    }

    private static int frequencyAt(float value, int[] frequencies) {
        int index = Math.max(0, Math.min(frequencies.length - 1, Math.round(value)));
        return frequencies[index];
    }

    private static int indexOf(int frequency, int[] frequencies) {
        for (int i = 0; i < frequencies.length; i++) {
            if (frequencies[i] == frequency) return i;
        }
        return 0;
    }
}
