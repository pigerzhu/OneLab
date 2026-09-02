package io.github.pigerzhu.onelab.feature.applications;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_COOLAPK_IMAGE_FULLSCREEN;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_SPLIT_IMAGE_FULLSCREEN;

import android.view.View;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Set;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.R;
import io.github.pigerzhu.onelab.navigation.AppListPage;
import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.SplitActionSwitchCard;
import io.github.pigerzhu.onelab.ui.Ui;

/** Preview UI for per-application image-viewer fullscreen support. */
public final class SplitImageFullscreenScreen {
    private static final Set<String> SUPPORTED_PACKAGES = Set.of("com.coolapk.market");
    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;
    private final AppListPage appList;

    public SplitImageFullscreenScreen(
            MainActivity host,
            Ui ui,
            SettingsStore settings,
            AppListPage appList
    ) {
        this.host = host;
        this.ui = ui;
        this.settings = settings;
        this.appList = appList;
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
        appList.show(
                host.getString(R.string.split_image_fullscreen_page_title),
                "",
                app -> isEnabled(KEY_ENABLE_COOLAPK_IMAGE_FULLSCREEN)
                        ? host.getString(R.string.split_image_fullscreen_enabled)
                        : host.getString(R.string.split_image_fullscreen_disabled),
                (app, refreshRow) -> {
                    boolean enabled = !isEnabled(KEY_ENABLE_COOLAPK_IMAGE_FULLSCREEN);
                    if (settings.setGlobal(
                            KEY_ENABLE_COOLAPK_IMAGE_FULLSCREEN, enabled ? "1" : "0")) {
                        refreshRow.run();
                    }
                },
                app -> isEnabled(KEY_ENABLE_COOLAPK_IMAGE_FULLSCREEN),
                null,
                app -> SUPPORTED_PACKAGES.contains(app.packageName));
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
