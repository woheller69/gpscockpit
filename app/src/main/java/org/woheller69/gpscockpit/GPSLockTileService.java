package org.woheller69.gpscockpit;

import static android.os.Build.VERSION.SDK_INT;
import static org.woheller69.gpscockpit.GpsSvc.ACTION_STOP_SERVICE;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import androidx.core.app.ActivityCompat;

public class GPSLockTileService extends TileService {
    // Called when the user adds your tile.
    @Override
    public void onTileAdded() {
        super.onTileAdded();
    }

    // Called when your app can update your tile.
    @Override
    public void onStartListening() {
        super.onStartListening();

        Tile qsTile;
        qsTile = getQsTile();
        if (SDK_INT >= Build.VERSION_CODES.Q) {
            qsTile.setSubtitle(MainActivity.gpsLocked ? getString(R.string.turned_on):getString(R.string.turned_off));
        }
        qsTile.setState(GpsSvc.mIsRunning ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        qsTile.updateTile();
    }

    // Called when your app can no longer update your tile.
    @Override
    public void onStopListening() {
        super.onStopListening();
    }

    // Called when the user taps on your tile in an active or inactive state.
    @Override
    public void onClick() {
        super.onClick();
        if (MainActivity.checkPermsOnly().isEmpty()) {
            if (GpsSvc.mIsRunning) {
                startService(new Intent(App.getCxt(), GpsSvc.class).setAction(ACTION_STOP_SERVICE));
                MainActivity.gpsLocked = false;
            } else {
                Intent intent = new Intent(App.getCxt(), GpsSvc.class);
                Intent intent2 = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
                intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                PendingIntent pi2 = PendingIntent.getActivity(App.getCxt(),0,intent2,PendingIntent.FLAG_IMMUTABLE);
                if (SDK_INT >= Build.VERSION_CODES.O) {
                    if ((ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) && (SDK_INT > Build.VERSION_CODES.TIRAMISU)){
                        Intent intent3 = new Intent(App.getCxt(), MainActivity.class);
                        intent3.setAction("RequestBackgroundLocation");
                        intent3.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        PendingIntent pi3 = PendingIntent.getActivity(App.getCxt(),0, intent3, PendingIntent.FLAG_IMMUTABLE);
                        startActivityAndCollapse(pi3);
                    } else {
                        if (SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            startActivityAndCollapse(pi2);
                        } else {
                            startActivityAndCollapse(intent2);
                        }
                        if (!GpsSvc.mIsRunning){
                            startForegroundService(intent);
                            MainActivity.gpsLocked = true;
                        }
                    }
                } else {
                    startActivityAndCollapse(intent2);
                    if (!GpsSvc.mIsRunning) {
                        startService(intent);
                        MainActivity.gpsLocked = true;
                    }
                }
            }
            Tile qsTile = getQsTile();
            if (SDK_INT >= Build.VERSION_CODES.Q) {
                qsTile.setSubtitle(MainActivity.gpsLocked ? getString(R.string.turned_on) : getString(R.string.turned_off));
            }
            qsTile.setState(MainActivity.gpsLocked ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            qsTile.updateTile();
        } else {
            Intent intent = new Intent(App.getCxt(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityAndCollapse(intent);
        }
    }

    // Called when the user removes your tile.
    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
    }

}
