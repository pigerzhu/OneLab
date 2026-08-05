package io.github.pigerzhu.onelab.feature.connectivity;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.R;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.DEFAULT_CAPTIVE_DELAY_MS;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_CAPTIVE_DELAY_MS;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_CAPTIVE_KEEPER;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.Ui;

public final class NetworkScreen {
    private static final int DELAY_UNLIMITED_STEP = 25;
    private static final int DELAY_MAX_SECONDS = 120;

    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;
    private TextView delayValue;

    public NetworkScreen(MainActivity host, Ui ui, SettingsStore settings) {
        this.host = host;
        this.ui = ui;
        this.settings = settings;
    }

    public View card() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);

        LinearLayout header = new LinearLayout(host);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(header, ui.matchWrap());

        LinearLayout copy = new LinearLayout(host);
        copy.setOrientation(LinearLayout.VERTICAL);
        header.addView(copy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        copy.addView(ui.text(host.getString(R.string.captive_keeper_title), 20, true,
                ui.colorOnSurface));
        copy.addView(ui.text(host.getString(R.string.captive_keeper_summary), 14, false,
                ui.colorOnSurfaceVariant));

        MaterialSwitch toggle = new MaterialSwitch(host);
        toggle.setChecked(isKeeperEnabled());
        toggle.setOnCheckedChangeListener((button, enabled) -> {
            settings.setGlobal(KEY_ENABLE_CAPTIVE_KEEPER, enabled ? "1" : "0");
            if (enabled && getDelayMs() <= 0L) {
                settings.setGlobal(KEY_CAPTIVE_DELAY_MS, String.valueOf(DEFAULT_CAPTIVE_DELAY_MS));
            }
        });
        header.addView(toggle);

        ui.addSpace(body, 14);
        delayValue = ui.text("", 24, true, ui.colorOnSurface);
        delayValue.setGravity(Gravity.CENTER);
        body.addView(delayValue, ui.matchWrap());

        Slider slider = new Slider(host);
        slider.setValueFrom(1f);
        slider.setValueTo(DELAY_UNLIMITED_STEP);
        slider.setStepSize(1f);
        slider.setLabelFormatter(value -> delayText(Math.round(value)));
        slider.setValue(sliderStepFromDelayMs(getDelayMs()));
        slider.addOnChangeListener((view, value, fromUser) -> {
            if (fromUser) updateDelayValue(Math.round(value));
        });
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                int step = Math.round(slider.getValue());
                settings.setGlobal(KEY_CAPTIVE_DELAY_MS, String.valueOf(delayMsFromStep(step)));
            }
        });
        body.addView(slider, ui.matchWrap());
        updateDelayValue(Math.round(slider.getValue()));
        return card;
    }

    private boolean isKeeperEnabled() {
        return "1".equals(settings.getGlobal(KEY_ENABLE_CAPTIVE_KEEPER, "0"));
    }

    private long getDelayMs() {
        try {
            return Long.parseLong(settings.getGlobal(
                    KEY_CAPTIVE_DELAY_MS, String.valueOf(DEFAULT_CAPTIVE_DELAY_MS)));
        } catch (NumberFormatException ignored) {
            return DEFAULT_CAPTIVE_DELAY_MS;
        }
    }

    private int sliderStepFromDelayMs(long delayMs) {
        if (delayMs == Long.MAX_VALUE) return DELAY_UNLIMITED_STEP;
        long seconds = Math.max(5L, Math.min(DELAY_MAX_SECONDS, delayMs / 1000L));
        return Math.round(seconds / 5f);
    }

    private long delayMsFromStep(int step) {
        return step >= DELAY_UNLIMITED_STEP ? Long.MAX_VALUE : Math.max(1, step) * 5_000L;
    }

    private String delayText(int step) {
        return step >= DELAY_UNLIMITED_STEP
                ? host.getString(R.string.captive_keeper_never_close)
                : host.getString(R.string.captive_keeper_seconds, Math.max(1, step) * 5);
    }

    private void updateDelayValue(int step) {
        if (delayValue != null) delayValue.setText(delayText(step));
    }
}
