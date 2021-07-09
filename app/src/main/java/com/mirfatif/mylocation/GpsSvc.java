package com.mirfatif.mylocation;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST;
import static androidx.core.app.NotificationManagerCompat.IMPORTANCE_DEFAULT;
import static com.mirfatif.mylocation.BuildConfig.APPLICATION_ID;
import static com.mirfatif.mylocation.Utils.formatLatLng;
import static com.mirfatif.mylocation.Utils.formatLocAccuracy;
import static com.mirfatif.mylocation.Utils.hasFineLocPerm;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.BigTextStyle;
import androidx.core.app.NotificationCompat.Builder;
import androidx.core.app.NotificationManagerCompat;
import java.util.concurrent.Future;

public class GpsSvc extends Service implements LocationListener, GpsStatus.Listener {

  public static final String ACTION_STOP_SERVICE = APPLICATION_ID + ".action.STOP_SERVICE";

  public static boolean mIsRunning = false;

  private final LocationManager mLocManager =
      (LocationManager) App.getCxt().getSystemService(Context.LOCATION_SERVICE);

  private final PowerManager mPowerManager =
      (PowerManager) App.getCxt().getSystemService(Context.POWER_SERVICE);

  private final NotificationManagerCompat mNotifManager =
      NotificationManagerCompat.from(App.getCxt());

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (hasFineLocPerm() && (intent == null || !ACTION_STOP_SERVICE.equals(intent.getAction()))) {
      showNotif();
      startGpsLocListener();
      mIsRunning = true;
      return START_STICKY;
    } else {
      stop();
      return START_NOT_STICKY;
    }
  }

  @Override
  public void onDestroy() {
    stop();
    super.onDestroy();
  }

  private Location mGpsLoc;

  @Override
  public void onLocationChanged(Location location) {
    mGpsLoc = location;
    updateNotification();
  }

  @Override
  public void onProviderEnabled(String provider) {
    mLastUpdate = 0;
    updateNotification();
  }

  @Override
  public void onProviderDisabled(String provider) {
    mLastUpdate = 0;
    updateNotification();
  }

  @Override
  public void onStatusChanged(String provider, int status, Bundle extras) {
    mLastUpdate = 0;
    updateNotification();
  }

  private void stop() {
    mIsRunning = false;
    stopGpsLocListener();
    if (mFuture != null) {
      mFuture.cancel(true);
    }
    stopSelf();
  }

  @Override
  public void onGpsStatusChanged(int event) {
    updateGpsSats();
  }

  private WakeLock mWakeLock;
  private Builder mNotifBuilder;

  private static final int NOTIF_ID = Utils.getInteger(R.integer.channel_gps_lock);
  private static final String CHANNEL_ID = "channel_gps_lock";
  private static final String CHANNEL_NAME = Utils.getString(R.string.channel_gps_lock);

  private void showNotif() {
    mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
    mWakeLock.acquire(30 * 60 * 1000L);

    Utils.createNotifChannel(CHANNEL_ID, CHANNEL_NAME, IMPORTANCE_DEFAULT);

    Intent intent = new Intent(App.getCxt(), MainActivity.class);
    PendingIntent pi =
        PendingIntent.getActivity(App.getCxt(), NOTIF_ID, intent, FLAG_UPDATE_CURRENT);

    mNotifBuilder =
        new Builder(App.getCxt(), CHANNEL_ID)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setSmallIcon(R.drawable.notification_icon)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // For N and below
            .setContentIntent(pi)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentTitle(getString(R.string.channel_gps_lock));

    if (VERSION.SDK_INT >= VERSION_CODES.Q) {
      startForeground(NOTIF_ID, mNotifBuilder.build(), FOREGROUND_SERVICE_TYPE_MANIFEST);
    } else {
      startForeground(NOTIF_ID, mNotifBuilder.build());
    }

    updateGpsSats();
    mLastUpdate = 0;
    updateNotification();
  }

  public static final long MIN_DELAY = 5000;

  @SuppressLint("MissingPermission")
  private void startGpsLocListener() {
    mLocManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_DELAY, 0, this);
    mLocManager.addGpsStatusListener(this);
  }

  private void stopGpsLocListener() {
    if (mWakeLock != null) {
      mWakeLock.release();
      mWakeLock = null;
    }
    mLocManager.removeUpdates(this);
    mLocManager.removeGpsStatusListener(this);
  }

  private final Object UPDATE_GPS_SATS_LOCK = new Object();
  private int mTotalSats, mSatsStrongSig, mUsedSats;

  @SuppressLint("MissingPermission")
  private void updateGpsSats() {
    synchronized (UPDATE_GPS_SATS_LOCK) {
      if (!hasFineLocPerm()) {
        stop();
        return;
      }
      GpsStatus gpsStatus = mLocManager.getGpsStatus(null);
      mTotalSats = mSatsStrongSig = mUsedSats = 0;
      for (GpsSatellite gpsSat : gpsStatus.getSatellites()) {
        mTotalSats++;
        if (gpsSat.getSnr() != 0) {
          mSatsStrongSig++;
        }
        if (gpsSat.usedInFix()) {
          mUsedSats++;
        }
      }
      updateNotification();
    }
  }

  private Future<?> mFuture;

  private synchronized void updateNotification() {
    if (mFuture != null) {
      mFuture.cancel(true);
    }
    mFuture = Utils.runBg(this::updateNotifBg);
  }

  private final Object NOTIF_UPDATE_LOCK = new Object();
  private long mLastUpdate;

  private void updateNotifBg() {
    synchronized (NOTIF_UPDATE_LOCK) {
      long sleep = 5000 + mLastUpdate - System.currentTimeMillis();
      if (sleep > 0) {
        try {
          NOTIF_UPDATE_LOCK.wait(sleep);
        } catch (InterruptedException e) {
          return;
        }
      }
      mLastUpdate = System.currentTimeMillis();

      String sText, bText;
      long when = 0;
      if (!mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        sText = bText = getString(R.string.turned_off);
      } else {
        sText = bText = getString(R.string.satellites_count, mTotalSats, mSatsStrongSig, mUsedSats);
        double lat, lng;
        if (mGpsLoc != null
            && (lat = mGpsLoc.getLatitude()) != 0
            && (lng = mGpsLoc.getLongitude()) != 0) {
          sText =
              getString(
                  R.string.location,
                  formatLatLng(lat),
                  formatLatLng(lng),
                  formatLocAccuracy(mGpsLoc.getAccuracy()));
          bText += "\n" + sText;
        }
        if (mGpsLoc != null && mGpsLoc.getTime() != 0) {
          when = mGpsLoc.getTime();
        }
      }
      mNotifBuilder.setContentText(sText);
      mNotifBuilder.setStyle(new BigTextStyle().bigText(bText));
      if (when != 0) {
        mNotifBuilder.setWhen(when);
        mNotifBuilder.setShowWhen(true);
      } else {
        mNotifBuilder.setShowWhen(false);
      }
      mNotifManager.notify(NOTIF_ID, mNotifBuilder.build());
    }
  }
}
