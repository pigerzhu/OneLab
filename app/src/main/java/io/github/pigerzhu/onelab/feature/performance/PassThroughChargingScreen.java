package io.github.pigerzhu.onelab.feature.performance;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.system.PassThroughChargingClient;
import io.github.pigerzhu.onelab.ui.Ui;

public final class PassThroughChargingScreen {
    private final MainActivity host;
    private final Ui ui;
    private final PassThroughChargingClient client;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public PassThroughChargingScreen(MainActivity host, Ui ui) {
        this.host = host;
        this.ui = ui;
        client = new PassThroughChargingClient(host);
    }

    public View card() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);

        MaterialSwitch toggle = new MaterialSwitch(host);
        body.addView(ui.switchRow(
                "USB PD 旁路供电",
                "可添加控制中心磁贴；需使用 25W 及以上三星超级快充电源。",
                toggle,
                20));
        setCheckedWithoutCallback(toggle, client.isEnabled());
        toggle.setOnCheckedChangeListener((button, enabled) -> apply(toggle, enabled));
        return card;
    }

    private void apply(MaterialSwitch toggle, boolean enabled) {
        toggle.setEnabled(false);
        executor.execute(() -> {
            boolean saved = client.setEnabled(enabled);
            boolean actual = client.isEnabled();
            host.runOnUiThread(() -> {
                setCheckedWithoutCallback(toggle, actual);
                toggle.setOnCheckedChangeListener(
                        (button, checked) -> apply(toggle, checked));
                toggle.setEnabled(true);
                Toast.makeText(host,
                        saved ? (actual ? "已请求开启旁路供电" : "已关闭旁路供电")
                                : "写入失败，请授予 root 权限",
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
