package io.github.pigerzhu.onelab.feature.applications;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_NETEASE_HALF_FOLD_PLAYER;

import android.view.View;
import android.widget.LinearLayout;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.R;
import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.Ui;

public final class NeteaseHalfFoldPlayerScreen {
    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;

    public NeteaseHalfFoldPlayerScreen(MainActivity host, Ui ui, SettingsStore settings) {
        this.host = host;
        this.ui = ui;
        this.settings = settings;
    }

    public View card() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);
        body.addView(ui.text(host.getString(R.string.netease_fold_title),
                20, true, ui.colorOnSurface));
        ui.addSpace(body, 8);
        MaterialSwitch toggle = new MaterialSwitch(host);
        toggle.setChecked("1".equals(settings.getGlobal(
                KEY_ENABLE_NETEASE_HALF_FOLD_PLAYER, "0")));
        toggle.setOnCheckedChangeListener((button, enabled) -> settings.setGlobal(
                KEY_ENABLE_NETEASE_HALF_FOLD_PLAYER, enabled ? "1" : "0"));
        body.addView(ui.switchRow(host.getString(R.string.netease_half_fold_player),
                host.getString(R.string.netease_half_fold_player_summary), toggle));
        return card;
    }
}
