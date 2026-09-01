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
    private final MainActivity host; private final Ui ui; private MaterialSwitch toggle;
    public QishuiMusicScreen(MainActivity host, Ui ui) { this.host = host; this.ui = ui; }
    public View card() {
        MaterialCardView card = ui.card(); LinearLayout body = ui.cardBody(); card.addView(body);
        toggle = new MaterialSwitch(host); toggle.setOnCheckedChangeListener((button, enabled) -> {
            toggle.setEnabled(false); Executors.newSingleThreadExecutor().execute(() -> {
                boolean ok = new QishuiMusicClient(host).setEnabled(enabled);
                host.runOnUiThread(() -> { toggle.setChecked(ok && enabled); toggle.setEnabled(true); });
            });
        });
        body.addView(ui.switchRow(host.getString(R.string.qishui_music_title), host.getString(R.string.qishui_music_summary), toggle));
        return card;
    }
}
