package dev.kendall.pocketguard;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class GuardTileService extends TileService {

    @Override public void onStartListening() {
        super.onStartListening();
        refresh();
    }

    @Override public void onClick() {
        super.onClick();
        if (!GuardService.isRunning()) {
            Intent i = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityAndCollapse(i);
            return;
        }
        Prefs.setEnabled(this, !Prefs.enabled(this));
        refresh();
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean running = GuardService.isRunning();
        boolean on = running && Prefs.enabled(this);
        t.setState(!running ? Tile.STATE_UNAVAILABLE : on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setSubtitle(!running ? "Service off" : on ? "On" : "Off");
        t.updateTile();
    }
}
