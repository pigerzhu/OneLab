package io.github.pigerzhu.onelab.feature.applications;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_COOLAPK_IMAGE_FULLSCREEN;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_SPLIT_IMAGE_FULLSCREEN;

import android.view.View;
import android.widget.LinearLayout;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.R;
import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.SplitActionSwitchCard;
import io.github.pigerzhu.onelab.ui.Ui;

/** Preview UI for per-application image-viewer fullscreen support. */
public final class SplitImageFullscreenScreen {
    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;

    public SplitImageFullscreenScreen(MainActivity host, Ui ui, SettingsStore settings) {
        this.host = host;
        this.ui = ui;
        this.settings = settings;
    }

    public View entryCard() {
        MaterialSwitch master = new MaterialSwitch(host);
        master.setContentDescription(host.getString(R.string.split_image_fullscreen_title));
        master.setChecked(isEnabled(KEY_ENABLE_SPLIT_IMAGE_FULLSCREEN));
        master.setOnCheckedChangeListener((button, enabled) ->
                persistToggle(master, KEY_ENABLE_SPLIT_IMAGE_FULLSCREEN, enabled));
        return new SplitActionSwitchCard(
                host,
                ui,
                host.getString(R.string.split_image_fullscreen_title),
                null,
                master,
                this::showPage);
    }

    private void showPage() {
        host.setNestedBackAction(() -> host.showSamsungAppsPage(true));
        LinearLayout root = host.beginSubPage(
                host.getString(R.string.split_image_fullscreen_page_title), "", 1);

        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);
        MaterialSwitch coolapk = new MaterialSwitch(host);
        coolapk.setChecked(isEnabled(KEY_ENABLE_COOLAPK_IMAGE_FULLSCREEN));
        coolapk.setOnCheckedChangeListener((button, enabled) ->
                persistToggle(coolapk, KEY_ENABLE_COOLAPK_IMAGE_FULLSCREEN, enabled));
        body.addView(ui.switchRow(
                host.getString(R.string.split_image_fullscreen_coolapk),
                null,
                coolapk,
                20));
        root.addView(card);
    }

    private boolean isEnabled(String key) {
        return "1".equals(settings.getGlobal(key, "0"));
    }

    private void persistToggle(MaterialSwitch toggle, String key, boolean enabled) {
        if (settings.setGlobal(key, enabled ? "1" : "0")) return;
        toggle.setOnCheckedChangeListener(null);
        toggle.setChecked(!enabled);
        toggle.setOnCheckedChangeListener((button, checked) ->
                persistToggle(toggle, key, checked));
    }
}
