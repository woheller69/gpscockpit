package org.woheller69.gpscockpit;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.location.LocationManager.GPS_PROVIDER;
import static android.os.Build.VERSION.SDK_INT;
import static org.woheller69.gpscockpit.GpsSvc.ACTION_STOP_SERVICE;
import static org.woheller69.gpscockpit.MySettings.SETTINGS;
import static org.woheller69.gpscockpit.Utils.copyLoc;
import static org.woheller69.gpscockpit.Utils.shareLoc;
import static org.woheller69.gpscockpit.Utils.hasCoarseLocPerm;
import static org.woheller69.gpscockpit.Utils.hasFineLocPerm;
import static org.woheller69.gpscockpit.Utils.isNaN;
import static org.woheller69.gpscockpit.Utils.openMap;
import static org.woheller69.gpscockpit.Utils.setNightTheme;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.OnNmeaMessageListener;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.github.anastr.speedviewlib.components.Section;
import org.woheller69.gpscockpit.databinding.ActivityMainBinding;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

  private ActivityMainBinding mB;
  public static final long MIN_DELAY = 2000;
  private final LocationManager mLocManager =
      (LocationManager) App.getCxt().getSystemService(Context.LOCATION_SERVICE);

  private boolean mGpsProviderSupported = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    setTheme(R.style.AppTheme);
    super.onCreate(savedInstanceState);
    if (setNightTheme(this)) {
      return;
    }
    mB = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(mB.getRoot());

    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayUseLogoEnabled(true);
      actionBar.setDisplayShowHomeEnabled(true);
    }

    for (String provider : mLocManager.getAllProviders()) {
      if (provider.equals(GPS_PROVIDER)) {
        mGpsProviderSupported = true;
        break;
      }
    }

    mB.gpsCont.deluxeSpeedView.clearSections();
    mB.gpsCont.deluxeSpeedView.addSections(
              new Section(.0f, 1.0f, ContextCompat.getColor(this, R.color.accent),10));
    setupGps();
    updateGpsUi();
    checkPerms();

    mB.grantPerm.setOnClickListener(v -> Utils.openAppSettings(this, getPackageName()));

  }

  @Override
  protected void onStart() {
    super.onStart();
    startLocListeners();
    setTimer();
    setGrantPermButtonState();
  }

  @Override
  protected void onStop() {
    stopTimer();
    stopLocListeners();
    super.onStop();
  }

  @Override
  protected void onResume() {
    super.onResume();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    FragmentManager fm = getSupportFragmentManager();
    Fragment frag = fm.findFragmentByTag(SATS_DIALOG_TAG);
    if (frag != null) {
      fm.beginTransaction().remove(frag).commitNowAllowingStateLoss();
    }
    super.onSaveInstanceState(outState);
  }

  @Override
  public void onBackPressed() {
    if (VERSION.SDK_INT == VERSION_CODES.Q) {
      // Bug: https://issuetracker.google.com/issues/139738913
      finishAfterTransition();
    } else {
      super.onBackPressed();
    }
  }

  @SuppressLint("RestrictedApi")
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main_overflow, menu);
    MenuCompat.setGroupDividerEnabled(menu, true);
    if (menu instanceof MenuBuilder) {
      ((MenuBuilder) menu).setOptionalIconsVisible(true);
    }
    menu.findItem(R.id.action_dark_theme).setChecked(SETTINGS.getForceDarkMode());
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int itemId = item.getItemId();
    if (itemId == R.id.action_dark_theme) {
      SETTINGS.setForceDarkMode(!item.isChecked());
      setNightTheme(this);
      return true;
    }
    if (itemId == R.id.action_about) {
      AboutDialogFragment.show(this);
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void attachBaseContext(Context context) {
    super.attachBaseContext(Utils.setLocale(context));
  }

  //////////////////////////////////////////////////////////////////
  ////////////////////////// LOC PROVIDERS /////////////////////////
  //////////////////////////////////////////////////////////////////

  private void setupGps() {
    if (!mGpsProviderSupported) {
      return;
    }
    startGpsLocListener();
    setTimer();

    mB.clearAgps.setOnClickListener(v -> clearAGPSData());
    mB.lockGps.setOnClickListener(
        v -> {
          if (mB.lockGps.isChecked()) {
            Intent intent = new Intent(App.getCxt(), GpsSvc.class);
            // If startForeground() in Service is called on UI thread, it won't show notification
            // unless Service is started with startForegroundService().
            if (SDK_INT >= VERSION_CODES.O) {
              startForegroundService(intent);
              startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:"+getPackageName())));
            } else {
              startService(intent);
            }
          } else {
            startService(new Intent(App.getCxt(), GpsSvc.class).setAction(ACTION_STOP_SERVICE));
          }
        });

    mB.gpsCont.map.setOnClickListener(v -> openMap(this, mGpsLocation));
    mB.gpsCont.copy.setOnClickListener(v -> copyLoc(mGpsLocation));
    mB.gpsCont.share.setOnClickListener(v -> shareLoc(this, mGpsLocation));

    if (GpsSvc.mIsRunning) {
      mB.lockGps.setChecked(true);
    }

    mB.gpsCont.satDetail.setOnClickListener(v -> showSatsDialog());

    Utils.setTooltip(mB.gpsCont.map);
    Utils.setTooltip(mB.gpsCont.copy);
    Utils.setTooltip(mB.gpsCont.share);
    Utils.setTooltip(mB.gpsCont.satDetail);
  }


  private final Object LOC_LISTENER_LOCK = new Object();

  private void startLocListeners() {
    startGpsLocListener();
  }

  private LocListener mGpsLocListener;
  private GnssStatus.Callback mGnssStatusCallback;
  private OnNmeaMessageListener mOnNmeaMessageListener;


  @SuppressLint("MissingPermission")
  private void startGpsLocListener() {
    synchronized (LOC_LISTENER_LOCK) {
      stopGpsLocListener();
      if (mGpsProviderSupported && hasFineLocPerm()) {
        mGpsLocListener = new LocListener(true);
        mLocManager.requestLocationUpdates(GPS_PROVIDER, MIN_DELAY, 0, mGpsLocListener);
        mGnssStatusCallback = new GnssStatus.Callback() {
          @Override
          public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
            synchronized (mSats) {
              mSats.clear();
              for (int i = 0; i < status.getSatelliteCount(); i++) {
                mSats.add(new Sat(status.getSvid(i), status.usedInFix(i), status.getCn0DbHz(i)));
              }
              Collections.sort(mSats, (s1, s2) -> Float.compare(s2.mSnr, s1.mSnr));
            }
            super.onSatelliteStatusChanged(status);
          }
        };

        mLocManager.registerGnssStatusCallback(mGnssStatusCallback,new Handler(Looper.getMainLooper()));

          mOnNmeaMessageListener = (message, timestamp) -> {
            Double msl = getAltitudeMeanSeaLevel(message);
            if (msl != null) {
              mNmeaAltitude = msl;
            }
          };
          mLocManager.addNmeaListener(mOnNmeaMessageListener, new Handler(Looper.getMainLooper()));
      }
    }
  }


  private void stopLocListeners() {
    stopGpsLocListener();
  }

  private void stopGpsLocListener() {
    synchronized (LOC_LISTENER_LOCK) {
      if (mGpsLocListener != null) {
        mLocManager.removeUpdates(mGpsLocListener);
        mLocManager.removeNmeaListener(mOnNmeaMessageListener);
        mGpsLocListener = null;
      }
      if (mGnssStatusCallback != null) {
        mLocManager.unregisterGnssStatusCallback(mGnssStatusCallback);
        mGnssStatusCallback = null;
      }
      clearGpsData();
    }
  }

  private void clearGpsData() {
    mGpsLocation = null;
    mNmeaAltitude = null;
    synchronized (mSats) {
      mSats.clear();
    }
  }


  //////////////////////////////////////////////////////////////////
  /////////////////////////////// UI //////////////////////////////
  //////////////////////////////////////////////////////////////////

  private final List<Sat> mSats = new ArrayList<>();

  private Timer mTimer;
  private long mPeriod;

  private void setTimer() {
    mPeriod = 5000;
    startTimer();
  }

  private void startTimer() {
    stopTimer();
    mTimer = new Timer();
    mTimer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            Utils.runUi(MainActivity.this, () -> updateUi());
          }
        },
        0,
        mPeriod);
  }

  private void stopTimer() {
    if (mTimer != null) {
      mTimer.cancel();
      mTimer = null;
    }
  }

  private Location mGpsLocation;
  private Double mNmeaAltitude;

  private void updateUi() {
    if (mB != null) {
      updateGpsUi();
    }
  }

  private void updateGpsUi() {
    String state = null, lat = "--", lng = "--", acc = "--", time = "--", altMSL = "--";
    boolean hasFineLocPerm = false, showSats = false, locAvailable = false;
    float bearing = 0;
    float speedval = -1;
    if (!mGpsProviderSupported) {
      state = getString(R.string.not_supported);
    } else {
      hasFineLocPerm = hasFineLocPerm();
      if (!hasFineLocPerm) {
        state = getString(R.string.perm_not_granted);
      } else if (!mLocManager.isProviderEnabled(GPS_PROVIDER)) {
        state = getString(R.string.turned_off);
      } else {
        showSats = !mSats.isEmpty();
        if (mGpsLocation != null
            && !isNaN(mGpsLocation.getLatitude())
            && !isNaN(mGpsLocation.getLongitude())) {
          locAvailable = true;
          lat = Utils.formatLatLng(mGpsLocation.getLatitude());
          lng = Utils.formatLatLng(mGpsLocation.getLongitude());
          if (mNmeaAltitude!=null) altMSL = Utils.formatInt(mNmeaAltitude )+ " m";
          if (mGpsLocation.hasSpeed()) {
              speedval = mGpsLocation.getSpeed() * 3.6f;
          }
          if (mGpsLocation.hasAccuracy()) {
            acc = getString(R.string.acc_unit, Utils.formatLocAccuracy(mGpsLocation.getAccuracy()));
          }
          if (mGpsLocation.hasBearing()) {
            mB.gpsCont.compass.setLineColor(ContextCompat.getColor(this,R.color.accent));
            mB.gpsCont.compass.setTextColor(ContextCompat.getColor(this,R.color.dynamicText));
            mB.gpsCont.compass.setShowMarker(true);
            bearing = mGpsLocation.getBearing();
          } else {
            mB.gpsCont.compass.setLineColor(ContextCompat.getColor(this,R.color.disabledStateColor));
            mB.gpsCont.compass.setTextColor(ContextCompat.getColor(this,R.color.disabledStateColor));
            mB.gpsCont.compass.setShowMarker(false);
          }
          long curr = System.currentTimeMillis()/1000;
          long t = mGpsLocation.getTime()/1000;
          t=Math.max(0, curr -t);
          time = t + " s ago";
        }
      }
    }
    mB.clearAgps.setEnabled(hasFineLocPerm);
    mB.lockGps.setEnabled(hasFineLocPerm);
    mB.gpsCont.map.setEnabled(locAvailable);
    mB.gpsCont.copy.setEnabled(locAvailable);
    mB.gpsCont.share.setEnabled(locAvailable);
    mB.gpsCont.stateV.setText(state);
    mB.gpsCont.latV.setText(lat);
    mB.gpsCont.lngV.setText(lng);
    mB.gpsCont.altitudeMSL.setText(altMSL);
    mB.gpsCont.deluxeSpeedView.speedTo(speedval);
    mB.gpsCont.accV.setText(acc);
    mB.gpsCont.timeV.setText(time);
    mB.gpsCont.satDetail.setEnabled(showSats);
    if (mGpsLocation!=null && mGpsLocation.hasBearing()) mB.gpsCont.compass.setDegrees(bearing);

    int total, good = 0, used = 0;
    synchronized (mSats) {
      total = mSats.size();
      for (Sat sat : mSats) {
        if (sat.mSnr != 0) {
          good++;
        }
        if (sat.mUsed) {
          used++;
        }
      }
    }

    mB.gpsCont.totalSatV.setText(String.valueOf(total));
    mB.gpsCont.goodSatV.setText(String.valueOf(good));
    mB.gpsCont.usedSatV.setText(String.valueOf(used));

    synchronized (SATS_DIALOG_TAG) {
      if (mSatsDialog != null) {
        if (showSats) {
          mSatsDialog.submitList(mSats);
        } else {
          mSatsDialog.dismissAllowingStateLoss();
        }
      }
    }
  }

  private void setGrantPermButtonState() {
    if (mB != null) {
      if (hasFineLocPerm() && hasCoarseLocPerm()) {
        mB.grantPerm.setVisibility(View.GONE);
      } else {
        mB.grantPerm.setVisibility(View.VISIBLE);
      }
    }
  }

  private SatsDialogFragment mSatsDialog;
  private static final String SATS_DIALOG_TAG = "SATELLITES_DETAIL";

  private void showSatsDialog() {
    mSatsDialog = new SatsDialogFragment();
    mSatsDialog.setOnDismissListener(
        d -> {
          synchronized (SATS_DIALOG_TAG) {
            mSatsDialog = null;
          }
        });
    mSatsDialog.showNow(getSupportFragmentManager(), SATS_DIALOG_TAG);
    mSatsDialog.submitList(mSats);
  }

  //////////////////////////////////////////////////////////////////
  /////////////////////////// PERM REQUEST /////////////////////////
  //////////////////////////////////////////////////////////////////

  private void checkPerms() {
    List<String> perms = new ArrayList<>();
    if (!hasFineLocPerm()) {
      perms.add(permission.ACCESS_FINE_LOCATION);
    }
    if (!hasCoarseLocPerm()) {
      perms.add(permission.ACCESS_COARSE_LOCATION);
    }
    if (!perms.isEmpty()) {
      ActivityCompat.requestPermissions(this, perms.toArray(new String[] {}), 0);
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    List<Integer> results = new ArrayList<>();
    for (int i : grantResults) {
      results.add(i);
    }
    if (results.contains(PERMISSION_GRANTED)) {
      startLocListeners();
      setGrantPermButtonState();
      setTimer();
    }
  }

  //////////////////////////////////////////////////////////////////
  ////////////////////////////// OTHER /////////////////////////////
  //////////////////////////////////////////////////////////////////


  private void clearAGPSData() {
    if (hasFineLocPerm()) {
      mLocManager.sendExtraCommand(GPS_PROVIDER, "delete_aiding_data", null);
      mLocManager.sendExtraCommand(GPS_PROVIDER, "force_time_injection", null);
      String command = SDK_INT >= VERSION_CODES.Q ? "force_psds_injection" : "force_xtra_injection";
      mLocManager.sendExtraCommand(GPS_PROVIDER, command, null);
      Utils.showShortToast(R.string.cleared);
    }
  }

  private class LocListener implements LocationListener {

    private final boolean mIsGps;

    private LocListener(boolean isGps) {
      mIsGps = isGps;
    }

    @Override
    public void onLocationChanged(Location location) {
      if (mIsGps) {
        mGpsLocation = location;
        updateUi();
      }
    }

    @Override
    public void onProviderEnabled(String provider) {
      setTimer();
    }

    @Override
    public void onProviderDisabled(String provider) {
      if (mIsGps) {
        clearGpsData();
      }
      setTimer();
    }

  }

  static class Sat {

    final int mPrn;
    final boolean mUsed;
    final float mSnr;

    Sat(int prn, boolean used, float snr) {
      mPrn = prn;
      mUsed = used;
      mSnr = snr;
      if (snr > maxSnr) {
        maxSnr = snr + correction;
      } else if (snr < minSnr) {
        minSnr = snr;
        if (minSnr < 0) {
          correction = -minSnr;
        }
      }
    }

    static float maxSnr;
    private static float minSnr, correction;
  }

  //////////////////////////////////////////////////////////////////
  ////////////////////////// FOR SUBCLASSES ////////////////////////
  //////////////////////////////////////////////////////////////////

  ActivityMainBinding getRootView() {
    return mB;
  }

  /* Taken from https://github.com/barbeau/gpstest/
   * Copyright (C) 2013-2019 Sean J. Barbeau
   *
   * Licensed under the Apache License, Version 2.0 (the "License");
   * you may not use this file except in compliance with the License.
   * You may obtain a copy of the License at
   *
   *      http://www.apache.org/licenses/LICENSE-2.0
   *
   * Unless required by applicable law or agreed to in writing, software
   * distributed under the License is distributed on an "AS IS" BASIS,
   * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   * See the License for the specific language governing permissions and
   * limitations under the License.
   */
  public static Double getAltitudeMeanSeaLevel(String nmeaSentence) {
    final int ALTITUDE_INDEX = 9;
    String[] tokens = nmeaSentence.split(",");

    if (nmeaSentence.startsWith("$GPGGA") || nmeaSentence.startsWith("$GNGNS") || nmeaSentence.startsWith("$GNGGA")) {
      String altitude;
      try {
        altitude = tokens[ALTITUDE_INDEX];
      } catch (ArrayIndexOutOfBoundsException e) {
        //Log.d("Nmea", "Bad NMEA sentence for geoid altitude - " + nmeaSentence + " :" + e);
        return null;
      }
      if (!TextUtils.isEmpty(altitude)) {
        Double altitudeParsed = null;
        try {
          altitudeParsed = Double.parseDouble(altitude);
        } catch (NumberFormatException e) {
          //Log.d("Nmea", "Bad geoid altitude value of '" + altitude + "' in NMEA sentence " + nmeaSentence + " :" + e);
        }
        return altitudeParsed;
      } else {
        //Log.d("Nmea", "Couldn't parse geoid altitude from NMEA: " + nmeaSentence);
        return null;
      }
    } else {
      //Log.d("Nmea", "Input must be $GPGGA, $GNGNS, or $GNGGA NMEA: " + nmeaSentence);
      return null;
    }
  }
}