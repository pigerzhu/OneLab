package io.github.pigerzhu.onelab.feature.applications;

import android.view.View;
import android.widget.LinearLayout;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.util.concurrent.Executors;
import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.R;
import io.github.pigerzhu.onelab.system.QishuiMusicClient;
import io.github.pigerzhu.onelab.ui.Ui;

public final class QishuiMusicScreen {
    private static final java.util.concurrent.ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor();
    private final MainActivity host; private final Ui ui; private MaterialSwitch toggle;
    private final QishuiMusicClient client;
    public QishuiMusicScreen(MainActivity host, Ui ui) {
        this.host = host; this.ui = ui; this.client = new QishuiMusicClient(host);
    }
    public View card() {
        MaterialCardView card = ui.card(); LinearLayout body = ui.cardBody(); card.addView(body);
        toggle = new MaterialSwitch(host);
        setToggleChecked(client.isEnabled());
        attachListener();
        body.addView(ui.switchRow(host.getString(R.string.qishui_music_title), null, toggle));
        return card;
    }

    private void attachListener() {
        toggle.setOnCheckedChangeListener((button, enabled) -> {
            boolean previous = !enabled;
            toggle.setEnabled(false);
            EXECUTOR.execute(() -> {
                boolean ok = client.setEnabled(enabled);
                host.runOnUiThread(() -> {
                    setToggleChecked(ok ? enabled : previous);
                    attachListener();
                    toggle.setEnabled(true);
                });
            });
        });
    }

    private void setToggleChecked(boolean checked) {
        toggle.setOnCheckedChangeListener(null);
        toggle.setChecked(checked);
    }
}
