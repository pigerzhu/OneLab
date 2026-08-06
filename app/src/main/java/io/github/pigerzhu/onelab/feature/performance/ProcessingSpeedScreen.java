package io.github.pigerzhu.onelab.feature.performance;

import io.github.pigerzhu.onelab.R;

import io.github.pigerzhu.onelab.MainActivity;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.pigerzhu.onelab.system.ProcessingSpeedClient;
import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.ChoiceGroup;
import io.github.pigerzhu.onelab.ui.Ui;

public final class ProcessingSpeedScreen {
    private static final String KEY_ENHANCED_PROCESSING = "enhanced_processing";
    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;
    private final ProcessingSpeedClient client;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private MaterialSwitch tileSwitch;

    public ProcessingSpeedScreen(MainActivity host, Ui ui, SettingsStore settings) {
        this.host = host;
        this.ui = ui;
        this.settings = settings;
        this.client = new ProcessingSpeedClient(host);
    }

    public View card() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);

        body.addView(ui.text(host.getString(R.string.processing_speed_title), 20, true,
                ui.colorOnSurface));
        body.addView(ui.text(host.getString(R.string.processing_speed_summary), 14, false,
                ui.colorOnSurfaceVariant));

        ui.addSpace(body, 14);
        ChoiceGroup speedGroup = new ChoiceGroup(host, ui);
        body.addView(speedGroup, ui.matchWrap());
        speedGroup.addOption(host.getString(R.string.processing_speed_optimized),
                host.getString(R.string.processing_speed_optimized_summary), 0);
        speedGroup.addOption(host.getString(R.string.processing_speed_high),
                host.getString(R.string.processing_speed_high_summary), 1);
        speedGroup.addOption(host.getString(R.string.processing_speed_maximum),
                host.getString(R.string.processing_speed_maximum_summary), 2);
        speedGroup.setValue(settings.getGlobalInt(KEY_ENHANCED_PROCESSING, 0));
        speedGroup.setOnChoiceChangedListener(value ->
                settings.setGlobal(KEY_ENHANCED_PROCESSING, String.valueOf(value)));

        ui.addSpace(body, 14);
        LinearLayout actions = new LinearLayout(host);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(actions, ui.matchWrap());

        LinearLayout tileSwitchRow = new LinearLayout(host);
        tileSwitchRow.setGravity(Gravity.CENTER_VERTICAL);
        tileSwitchRow.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(tileSwitchRow, new LinearLayout.LayoutParams(0, ui.dp(46), 1));
        tileSwitchRow.addView(ui.text(host.getString(R.string.processing_speed_native_tile), 14,
                        true, ui.colorOnSurface),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        tileSwitch = new MaterialSwitch(host);
        tileSwitch.setChecked(isNativeComponentsEnabled());
        tileSwitch.setOnCheckedChangeListener((button, enabled) -> setNativeComponents(enabled));
        tileSwitchRow.addView(tileSwitch);

        Space space = new Space(host);
        actions.addView(space, new LinearLayout.LayoutParams(ui.dp(10), 1));

        MaterialButton openNativeButton = ui.actionButton(
                host.getString(R.string.processing_speed_open_native));
        openNativeButton.setOnClickListener(v -> openNativePage());
        actions.addView(openNativeButton, new LinearLayout.LayoutParams(0, ui.dp(46), 1));
        syncTileSwitch();
        return card;
    }

    private void setNativeComponents(boolean enabled) {
        tileSwitch.setEnabled(false);
        executor.execute(() -> {
            boolean ok = client.setNativeComponentsEnabled(enabled);
            host.runOnUiThread(() -> {
                if (!ok) setTileSwitchChecked(!enabled);
                tileSwitch.setEnabled(true);
                Toast.makeText(host,
                        ok ? (enabled ? R.string.processing_speed_tile_enabled
                                : R.string.processing_speed_tile_disabled)
                                : R.string.toast_action_failed_root,
                        ok ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                syncTileSwitch();
            });
        });
    }

    private boolean isNativeComponentsEnabled() {
        return client.isNativeComponentsEnabled();
    }

    private void syncTileSwitch() {
        boolean enabled = isNativeComponentsEnabled();
        if (tileSwitch != null && tileSwitch.isChecked() != enabled) {
            setTileSwitchChecked(enabled);
        }
    }

    private void setTileSwitchChecked(boolean enabled) {
        tileSwitch.setOnCheckedChangeListener(null);
        tileSwitch.setChecked(enabled);
        tileSwitch.setOnCheckedChangeListener(
                (button, checked) -> setNativeComponents(checked));
    }

    private void openNativePage() {
        Intent intent = new Intent(ProcessingSpeedClient.ACTION_ENHANCED_PROCESSING);
        if (host.getPackageManager().resolveActivity(intent, 0) != null) {
            try {
                host.startActivity(intent);
                return;
            } catch (SecurityException ignored) {
                // This activity requires Samsung's signature permission on some builds.
            }
        }
        executor.execute(() -> {
            boolean ok = client.openNativePageWithRoot();
            host.runOnUiThread(() -> Toast.makeText(host,
                    ok ? R.string.processing_speed_native_opened
                            : R.string.processing_speed_native_open_failed,
                    Toast.LENGTH_SHORT).show());
        });
    }

    public void onDestroy() {
        executor.shutdownNow();
    }
}
