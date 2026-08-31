package io.github.pigerzhu.onelab.feature.applications;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_QISHUI_LARGE_SCREEN;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_QISHUI_PAD_PLAYER_LAYOUT;

import android.view.View;
import android.widget.LinearLayout;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.R;
import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.ExpandableSwitchGroup;
import io.github.pigerzhu.onelab.ui.Ui;

public final class QishuiLargeScreenScreen {
    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;

    public QishuiLargeScreenScreen(MainActivity host, Ui ui, SettingsStore settings) {
        this.host = host;
        this.ui = ui;
        this.settings = settings;
    }

    public View card() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);

        boolean parentEnabled = "1".equals(settings.getGlobal(
                KEY_ENABLE_QISHUI_LARGE_SCREEN, "0"));
        MaterialSwitch childToggle = new MaterialSwitch(host);
        childToggle.setChecked("1".equals(settings.getGlobal(
                KEY_ENABLE_QISHUI_PAD_PLAYER_LAYOUT, "0")));
        childToggle.setEnabled(parentEnabled);
        childToggle.setOnCheckedChangeListener((button, enabled) ->
                settings.setGlobal(KEY_ENABLE_QISHUI_PAD_PLAYER_LAYOUT, enabled ? "1" : "0"));

        MaterialSwitch parentToggle = new MaterialSwitch(host);
        parentToggle.setChecked(parentEnabled);
        parentToggle.setOnCheckedChangeListener((button, enabled) -> {
            settings.setGlobal(KEY_ENABLE_QISHUI_LARGE_SCREEN, enabled ? "1" : "0");
            childToggle.setEnabled(enabled);
        });

        View childRow = ui.switchRow(
                host.getString(R.string.qishui_pad_player_layout), null, childToggle, 15);
        childRow.setPadding(ui.dp(40), 0, 0, 0);
        body.addView(new ExpandableSwitchGroup(
                host,
                ui,
                host.getString(R.string.qishui_large_screen_title),
                null,
                parentToggle,
                childRow));
        return card;
    }
}
