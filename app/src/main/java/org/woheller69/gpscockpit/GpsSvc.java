package org.woheller69.gpscockpit;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST;
import static androidx.core.app.NotificationManagerCompat.IMPORTANCE_DEFAULT;
import static org.woheller69.gpscockpit.BuildConfig.APPLICATION_ID;
import static org.woheller69.gpscockpit.Utils.formatLocAccuracy;
import static org.woheller69.gpscockpit.MySettings.SETTINGS;
import static org.woheller69.gpscockpit.Utils.getPiFlags;
import static org.woheller69.gpscockpit.Utils.hasFineLocPerm;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.OnNmeaMessageListener;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.BigTextStyle;
import androidx.core.app.NotificationCompat.Builder;
import androidx.core.app.NotificationManagerCompat;

import java.util.concurrent.Future;

public class GpsSvc extends Service implements LocationListener {

  public static final String ACTION_STOP_SERVICE = APPLICATION_ID + ".action.STOP_SERVICE";

  public static boolean mIsRunning = false;

  private GnssStatus.Callback mGnssStatusCallback;

  private OnNmeaMessageListener mOnNmeaMessageListener;
  private Double mNmeaAltitude = null;

  private final LocationManager mLocManager =
      (LocationManager) App.getCxt().getSystemService(Context.LOCATION_SERVICE);

  private final PowerManager mPowerManager =
      (PowerManager) App.getCxt().getSystemService(Context.POWER_SERVICE);

  private final NotificationManagerCompat mNotifManager =
      NotificationManagerCompat.from(App.getCxt());

  @Deprecated
  @Override
  public void onStatusChanged(String provider, int status, Bundle extras){
  }

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

    super.onDestroy();
  }

  private Location mGpsLoc;
  private long mGpsLocTime;

  @Override
  public void onLocationChanged(Location location) {
    mGpsLoc = location;
    mGpsLocTime = System.currentTimeMillis();  // because location.getTime() gives wrong time
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

  private void stop() {
    mIsRunning = false;
    stopGpsLocListener();
    if (mFuture != null) {
      mFuture.cancel(true);
    }
    stopSelf();
  }

  private WakeLock mWakeLock;
  private Builder mNotifBuilder;

  private static final int NOTIF_ID = Utils.getInteger(R.integer.channel_gps_lock);
  private static final String CHANNEL_ID = "channel_gps_lock";
  private static final String CHANNEL_NAME = Utils.getString(R.string.channel_gps_lock);

  private void showNotif() {
    mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
    mWakeLock.acquire();

    Utils.createNotifChannel(CHANNEL_ID, CHANNEL_NAME, IMPORTANCE_DEFAULT);

    Intent intent = new Intent(App.getCxt(), MainActivity.class);
    intent.setAction(Intent.ACTION_MAIN);
    intent.addCategory(Intent.CATEGORY_LAUNCHER);
    PendingIntent pi = PendingIntent.getActivity(App.getCxt(), NOTIF_ID, intent, getPiFlags());

    mNotifBuilder =
        new Builder(App.getCxt(), CHANNEL_ID)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setSmallIcon(R.drawable.ic_gps_fixed_black_24dp)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // For N and below
            .setContentIntent(pi)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentTitle(getString(R.string.channel_gps_lock));

    if (VERSION.SDK_INT >= VERSION_CODES.Q) {
      startForeground(NOTIF_ID, mNotifBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
    } else {
      startForeground(NOTIF_ID, mNotifBuilder.build());
    }

    mLastUpdate = 0;
    updateNotification();
  }

  public static final long MIN_DELAY = 10000;

  @SuppressLint("MissingPermission")
  private void startGpsLocListener() {
    mGnssStatusCallback = new GnssStatus.Callback() {
      @Override
      public void onSatelliteStatusChanged(@NonNull GnssStatus status) {

        mTotalSats = mSatsStrongSig = mUsedSats = 0;
        for (int i=0;i<status.getSatelliteCount();i++) {
          mTotalSats++;
          if (status.getCn0DbHz(i) != 0) {
            mSatsStrongSig++;
          }
          if (status.usedInFix(i)) {
            mUsedSats++;
          }
        }
        if ((mUsedSats<4) || (mGpsLoc!=null && System.currentTimeMillis()-mGpsLocTime > 2*MIN_DELAY)) {
          mGpsLoc=null;  //delete last location if less then 4 sats are in use or last update time longer than 2*MIN_DELAY-> fix lost
          mNmeaAltitude=null;
        }
        updateNotification();
        super.onSatelliteStatusChanged(status);
      }
    };
    mLocManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_DELAY, 0, this);
    mLocManager.registerGnssStatusCallback(mGnssStatusCallback,new Handler(Looper.getMainLooper()));

    mOnNmeaMessageListener = (message, timestamp) -> {
      Double msl = MainActivity.getAltitudeMeanSeaLevel(message);
      if (msl != null) {
        mNmeaAltitude = msl;
      }
    };
    mLocManager.addNmeaListener(mOnNmeaMessageListener, new Handler(Looper.getMainLooper()));
  }

  private void stopGpsLocListener() {
    if (mWakeLock != null) {
      if (mWakeLock.isHeld()) {
        mWakeLock.release();
      }
      mWakeLock = null;
    }
    mLocManager.removeUpdates(this);
    mLocManager.removeNmeaListener(mOnNmeaMessageListener);
    mLocManager.unregisterGnssStatusCallback(mGnssStatusCallback);
  }

  private int mTotalSats, mSatsStrongSig, mUsedSats;


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
      long sleep = MIN_DELAY + mLastUpdate - System.currentTimeMillis();
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
      mNotifBuilder.setContentTitle(getString(R.string.channel_gps_lock));
      if (!mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        sText = bText = getString(R.string.turned_off);
      } else {
        sText = bText = getString(R.string.satellites_count, mTotalSats, mSatsStrongSig, mUsedSats);
        double lat, lng;
        if (mGpsLoc != null
            && (lat = mGpsLoc.getLatitude()) != 0
            && (lng = mGpsLoc.getLongitude()) != 0) {
          if (SETTINGS.getIntPref(R.string.pref_units_key, MainActivity.METRIC) == MainActivity.METRIC){
            sText = getString(R.string.accuracy) + getString(R.string.dist_unit, formatLocAccuracy(mGpsLoc.getAccuracy()));
            if (mNmeaAltitude!=null){
             mNotifBuilder.setContentTitle(getString(R.string.channel_gps_lock) + " \u26F0 "+ getString(R.string.dist_unit, Utils.formatInt(mNmeaAltitude )));
            }
          } else {
            sText = getString(R.string.accuracy) + getString(R.string.dist_unit_imperial, formatLocAccuracy(mGpsLoc.getAccuracy()*3.28084f));
            if (mNmeaAltitude!=null){
              mNotifBuilder.setContentTitle(getString(R.string.channel_gps_lock) + " \u26F0 "+ getString(R.string.dist_unit_imperial, Utils.formatInt(mNmeaAltitude*3.28084f)));
            }
          }
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
