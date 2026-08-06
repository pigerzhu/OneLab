package io.github.pigerzhu.onelab.feature.experiment;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_GPU_RANGE_EXPERIMENT;
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

import java.util.List;

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
                "GPU 频率范围",
                "限制 GPU 持续运行的最低与最高频率。",
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

        toggle.setChecked(settings.getGlobalInt(KEY_ENABLE_GPU_RANGE_EXPERIMENT, 0) == 1);
        boolean[] syncingToggle = {false};
        toggle.setOnCheckedChangeListener((button, enabled) -> {
            if (syncingToggle[0]) return;
            if (!settings.setGlobal(KEY_ENABLE_GPU_RANGE_EXPERIMENT, enabled ? "1" : "0")) {
                syncingToggle[0] = true;
                button.setChecked(!enabled);
                syncingToggle[0] = false;
                return;
            }
            status.setText(enabled ? "设置已保存，等待运行状态更新" : "未启用");
        });
        return card;
    }

    private GpuFrequencyRange currentRange() {
        return GpuFrequencyRange.normalize(
                settings.getGlobalInt(KEY_GPU_RANGE_MIN_MHZ, 80),
                settings.getGlobalInt(KEY_GPU_RANGE_MAX_MHZ, 1000));
    }

    private void saveRange(GpuFrequencyRange range) {
        boolean minSaved = settings.putGlobalQuietly(
                KEY_GPU_RANGE_MIN_MHZ, String.valueOf(range.minMhz()));
        boolean maxSaved = settings.putGlobalQuietly(
                KEY_GPU_RANGE_MAX_MHZ, String.valueOf(range.maxMhz()));
        Toast.makeText(host, minSaved && maxSaved ? "频率范围已保存" : "频率范围保存失败",
                Toast.LENGTH_SHORT).show();
    }

    private String statusText() {
        String status = settings.getGlobal(KEY_GPU_RANGE_RUNTIME_STATUS, "unavailable");
        if ("active".equals(status)) return "已生效";
        if ("disabled".equals(status)) return "未启用";
        return "当前设备不可用或等待重启验证";
    }

    private static GpuFrequencyRange rangeFrom(List<Float> values) {
        return GpuFrequencyRange.normalize(frequencyAt(values.get(0)), frequencyAt(values.get(1)));
    }

    private static String rangeText(GpuFrequencyRange range) {
        if (range.isLocked()) return "锁定 " + range.minMhz() + "MHz";
        return range.minMhz() + " - " + range.maxMhz() + "MHz";
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
