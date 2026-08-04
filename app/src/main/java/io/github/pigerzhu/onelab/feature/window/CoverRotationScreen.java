package io.github.pigerzhu.onelab.feature.window;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.contract.SettingsKeys;
import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.Ui;

public final class CoverRotationScreen {
    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public CoverRotationScreen(MainActivity host, Ui ui, SettingsStore settings) {
        this.host = host;
        this.ui = ui;
        this.settings = settings;
    }

    public View card() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);

        MaterialSwitch toggle = new MaterialSwitch(host);
        toggle.setChecked(settings.getGlobalInt(
                SettingsKeys.KEY_COVER_PORTRAIT_ONLY, 0) == 1);
        body.addView(ui.switchRow(
                "外屏保持竖屏",
                "内屏自动旋转，外屏固定竖屏；需启用系统界面作用域。",
                toggle,
                20));
        toggle.setOnCheckedChangeListener((button, enabled) ->
                apply(toggle, enabled));
        return card;
    }

    private void apply(MaterialSwitch toggle, boolean enabled) {
        toggle.setEnabled(false);
        executor.execute(() -> {
            boolean saved = settings.putGlobalQuietly(
                    SettingsKeys.KEY_COVER_PORTRAIT_ONLY,
                    enabled ? "1" : "0");
            host.runOnUiThread(() -> {
                setCheckedWithoutCallback(toggle, saved ? enabled : !enabled);
                toggle.setOnCheckedChangeListener((button, checked) ->
                        apply(toggle, checked));
                toggle.setEnabled(true);
                Toast.makeText(
                        host,
                        saved ? "已保存，启用系统界面作用域并重启后生效"
                                : "保存失败，请授予 WRITE_SECURE_SETTINGS 或 root",
                        saved ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
            });
        });
    }

    private static void setCheckedWithoutCallback(MaterialSwitch toggle, boolean checked) {
        toggle.setOnCheckedChangeListener(null);
        toggle.setChecked(checked);
    }

    public void onDestroy() {
        executor.shutdownNow();
    }
}
