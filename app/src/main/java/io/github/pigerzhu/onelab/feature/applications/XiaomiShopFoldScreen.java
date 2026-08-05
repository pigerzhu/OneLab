package io.github.pigerzhu.onelab.feature.applications;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.R;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_XIAOMI_SHOP_FOLD;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.Ui;

public final class XiaomiShopFoldScreen {
    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;

    public XiaomiShopFoldScreen(MainActivity host, Ui ui, SettingsStore settings) {
        this.host = host;
        this.ui = ui;
        this.settings = settings;
    }

    public View card() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);

        LinearLayout row = new LinearLayout(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(row, ui.matchWrap());

        LinearLayout copy = new LinearLayout(host);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        copy.addView(ui.text(
                host.getString(R.string.xiaomi_shop_fold_title), 20, true, ui.colorOnSurface));
        copy.addView(ui.text(
                host.getString(R.string.xiaomi_shop_fold_summary),
                14,
                false,
                ui.colorOnSurfaceVariant));

        MaterialSwitch toggle = new MaterialSwitch(host);
        toggle.setChecked("1".equals(
                settings.getGlobal(KEY_ENABLE_XIAOMI_SHOP_FOLD, "0")));
        toggle.setOnCheckedChangeListener((button, enabled) ->
                settings.setGlobal(
                        KEY_ENABLE_XIAOMI_SHOP_FOLD, enabled ? "1" : "0"));
        row.addView(toggle);
        return card;
    }
}
