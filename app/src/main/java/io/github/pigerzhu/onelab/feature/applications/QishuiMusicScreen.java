package io.github.pigerzhu.onelab.feature.applications;

import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.util.concurrent.Executors;
import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.R;
import io.github.pigerzhu.onelab.system.QishuiMusicClient;
import io.github.pigerzhu.onelab.ui.Ui;
import io.github.pigerzhu.onelab.ui.InfoBubbleButton;

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
        LinearLayout row = new LinearLayout(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(row, ui.matchWrap());
        row.addView(ui.text(host.getString(R.string.qishui_music_title), 20, true, ui.colorOnSurface));
        row.addView(new InfoBubbleButton(host, ui,
                host.getString(R.string.qishui_music_version_notice),
                host.getString(R.string.info_bubble_content_description)),
                new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)));
        View space = new View(host);
        row.addView(space, new LinearLayout.LayoutParams(0, 1, 1));
        row.addView(toggle);
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
