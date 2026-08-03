package io.github.pigerzhu.onelab.feature.applications;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_BAIDU_LARGE_SCREEN;

import android.view.View;
import android.widget.LinearLayout;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.Ui;

public final class BaiduLargeScreenScreen {
    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;

    public BaiduLargeScreenScreen(MainActivity host, Ui ui, SettingsStore settings) {
        this.host = host;
        this.ui = ui;
        this.settings = settings;
    }

    public View card() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);

        body.addView(ui.text("百度折叠屏适配", 20, true, ui.colorOnSurface));
        ui.addSpace(body, 8);

        MaterialSwitch toggle = new MaterialSwitch(host);
        toggle.setChecked("1".equals(settings.getGlobal(KEY_ENABLE_BAIDU_LARGE_SCREEN, "0")));
        toggle.setOnCheckedChangeListener((button, enabled) ->
                settings.setGlobal(KEY_ENABLE_BAIDU_LARGE_SCREEN, enabled ? "1" : "0"));
        body.addView(ui.switchRow(
                "启用完整大屏模式",
                "使用百度原生大窗口与平板首页，修改后需重启百度",
                toggle));
        return card;
    }
}
