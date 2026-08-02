package io.github.pigerzhu.onelab.feature.performance;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.pigerzhu.onelab.system.PassThroughChargingClient;

public final class PassThroughChargingTileService extends TileService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onStartListening() {
        super.onStartListening();
        executor.execute(() -> updateTile(
                new PassThroughChargingClient(this).isEnabled()));
    }

    @Override
    public void onClick() {
        super.onClick();
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.updateTile();
        }
        executor.execute(() -> {
            PassThroughChargingClient client = new PassThroughChargingClient(this);
            client.setEnabled(!client.isEnabled());
            updateTile(client.isEnabled());
        });
    }

    private void updateTile(boolean enabled) {
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setSubtitle(enabled ? "已开启" : "已关闭");
            tile.updateTile();
        }
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
