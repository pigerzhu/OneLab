package io.github.pigerzhu.onelab.feature.applications;

import io.github.pigerzhu.onelab.MainActivity;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_BILI_FOLD_GATE;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_BILI_TABLET_LAYOUT;

import android.view.View;
import android.widget.LinearLayout;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.Ui;

public final class BiliFoldGateScreen {
    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;

    public BiliFoldGateScreen(MainActivity host, Ui ui, SettingsStore settings) {
        this.host = host;
        this.ui = ui;
        this.settings = settings;
    }

    public View card() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);

        body.addView(ui.text("哔哩哔哩折叠屏适配", 20, true, ui.colorOnSurface));
        ui.addSpace(body, 8);

        boolean gateEnabled =
                "1".equals(settings.getGlobal(KEY_ENABLE_BILI_FOLD_GATE, "0"));
        MaterialSwitch tabletToggle = new MaterialSwitch(host);
        tabletToggle.setChecked(
                "1".equals(settings.getGlobal(KEY_ENABLE_BILI_TABLET_LAYOUT, "0")));
        tabletToggle.setEnabled(gateEnabled);

        MaterialSwitch gateToggle = new MaterialSwitch(host);
        gateToggle.setChecked(gateEnabled);
        gateToggle.setOnCheckedChangeListener((button, enabled) -> {
            settings.setGlobal(KEY_ENABLE_BILI_FOLD_GATE, enabled ? "1" : "0");
            tabletToggle.setEnabled(enabled);
        });
        body.addView(ui.switchRow("B 站原生大屏适配", null, gateToggle));

        tabletToggle.setOnCheckedChangeListener((button, enabled) ->
                settings.setGlobal(KEY_ENABLE_BILI_TABLET_LAYOUT, enabled ? "1" : "0"));
        body.addView(ui.switchRow("使用平板布局", null, tabletToggle));

        return card;
    }
}
