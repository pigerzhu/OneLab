package io.github.pigerzhu.onelab.feature.applications;

import android.view.View;
import android.widget.LinearLayout;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.R;
import io.github.pigerzhu.onelab.contract.SettingsKeys;
import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.ExpandableSwitchGroup;
import io.github.pigerzhu.onelab.ui.Ui;

public final class TikTokLargeScreenScreen {
    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;

    public TikTokLargeScreenScreen(MainActivity host, Ui ui, SettingsStore settings) {
        this.host = host;
        this.ui = ui;
        this.settings = settings;
    }

    public View card() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);
        body.addView(ui.text(host.getString(R.string.tiktok_fold_title), 20, true, ui.colorOnSurface));
        ui.addSpace(body, 8);
        MaterialSwitch portraitToggle = new MaterialSwitch(host);
        portraitToggle.setChecked("1".equals(settings.getGlobal(
                SettingsKeys.KEY_ENABLE_TIKTOK_PORTRAIT_LARGE_SCREEN, "0")));
        portraitToggle.setOnCheckedChangeListener((button, enabled) -> settings.setGlobal(
                SettingsKeys.KEY_ENABLE_TIKTOK_PORTRAIT_LARGE_SCREEN, enabled ? "1" : "0"));
        MaterialSwitch toggle = new MaterialSwitch(host);
        toggle.setChecked("1".equals(settings.getGlobal(
                SettingsKeys.KEY_ENABLE_TIKTOK_SIDE_COMMENTS, "0")));
        portraitToggle.setEnabled(toggle.isChecked());
        toggle.setOnCheckedChangeListener((button, enabled) -> {
            settings.setGlobal(SettingsKeys.KEY_ENABLE_TIKTOK_SIDE_COMMENTS,
                    enabled ? "1" : "0");
            portraitToggle.setEnabled(enabled);
        });
        View portraitRow = ui.switchRow(
                host.getString(R.string.tiktok_portrait_large_screen), null, portraitToggle, 15);
        portraitRow.setPadding(ui.dp(40), 0, 0, 0);
        body.addView(new ExpandableSwitchGroup(
                host,
                ui,
                host.getString(R.string.tiktok_side_comments),
                host.getString(R.string.tiktok_landscape_only),
                toggle,
                portraitRow));
        MaterialSwitch liveToggle = new MaterialSwitch(host);
        liveToggle.setChecked("1".equals(settings.getGlobal(
                SettingsKeys.KEY_ENABLE_TIKTOK_LIVE_MULTI_SCREEN, "0")));
        liveToggle.setOnCheckedChangeListener((button, enabled) -> settings.setGlobal(
                SettingsKeys.KEY_ENABLE_TIKTOK_LIVE_MULTI_SCREEN, enabled ? "1" : "0"));
        body.addView(ui.switchRow(host.getString(R.string.tiktok_live_multi_screen),
                host.getString(R.string.tiktok_landscape_only), liveToggle));
        return card;
    }

}
