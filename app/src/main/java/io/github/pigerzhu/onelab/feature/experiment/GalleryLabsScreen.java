package io.github.pigerzhu.onelab.feature.experiment;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.R;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_GALLERY_DEV_LABS;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_GALLERY_LABS_ZH_CN;

import android.view.View;
import android.widget.LinearLayout;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.ExpandableSwitchGroup;
import io.github.pigerzhu.onelab.ui.Ui;

public final class GalleryLabsScreen {
    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;

    public GalleryLabsScreen(MainActivity host, Ui ui, SettingsStore settings) {
        this.host = host;
        this.ui = ui;
        this.settings = settings;
    }

    public View card() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);

        MaterialSwitch translationToggle = new MaterialSwitch(host);
        translationToggle.setChecked("1".equals(settings.getGlobal(
                KEY_ENABLE_GALLERY_LABS_ZH_CN, "0")));
        translationToggle.setOnCheckedChangeListener((button, enabled) -> settings.setGlobal(
                KEY_ENABLE_GALLERY_LABS_ZH_CN, enabled ? "1" : "0"));
        MaterialSwitch toggle = new MaterialSwitch(host);
        toggle.setChecked("1".equals(settings.getGlobal(KEY_ENABLE_GALLERY_DEV_LABS, "0")));
        translationToggle.setEnabled(toggle.isChecked());
        toggle.setOnCheckedChangeListener((button, enabled) -> {
            settings.setGlobal(KEY_ENABLE_GALLERY_DEV_LABS, enabled ? "1" : "0");
            translationToggle.setEnabled(enabled);
        });

        View translationRow = ui.switchRow(
                host.getString(R.string.gallery_labs_zh_cn), null, translationToggle, 15);
        translationRow.setPadding(ui.dp(40), 0, 0, 0);
        body.addView(new ExpandableSwitchGroup(
                host,
                ui,
                host.getString(R.string.gallery_labs_title),
                host.getString(R.string.gallery_labs_summary),
                toggle,
                translationRow));

        return card;
    }
}
