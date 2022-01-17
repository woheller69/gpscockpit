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
import android.content.res.Configuration;
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
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.NonNull;
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

  private Menu mMenu;
  public static final int METRIC = 0;
  private static final int IMPERIAL = 1;
  private static final int NAUTICAL = 2;
  private final int[]defaultSpeedIndexList = {4, 3, 2};  //Metric, Imperial, Nautical
  private ActivityMainBinding mB;
  public static final long MIN_DELAY = 2000;
  private final LocationManager mLocManager =
      (LocationManager) App.getCxt().getSystemService(Context.LOCATION_SERVICE);

  private boolean mGpsProviderSupported = false;
  private boolean recording = false;
  private boolean gpsLocked = false;
  private boolean gpsLockedBeforeStart = false;
  private long mDebugCounter = 0;
  private final float[] speedList = {27,45,90,135,180,270};
  private Location mGpsLocation;
  private Location mOldGpsLocation;
  private float mTravelDistance = 0;
  private Double mAltUp = 0d;
  private Double mAltDown = 0d;
  private Double mNmeaOldAltitude;
  private Double mNmeaAltitude;
  private Float mMaxSpeed = 0f;
  private long mStartTime = System.currentTimeMillis()/1000;
  private long mEndTime = System.currentTimeMillis()/1000;

  @Override
  public void onConfigurationChanged(Configuration newConfig){
    super.onConfigurationChanged(newConfig);
    mB = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(mB.getRoot());
    setupSpeedView();
    mB.record.setChecked(recording);
    setupGps();
    updateGpsUi();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    setTheme(R.style.AppTheme);
    super.onCreate(savedInstanceState);
    setNightTheme(this);
    mB = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(mB.getRoot());

    for (String provider : mLocManager.getAllProviders()) {
      if (provider.equals(GPS_PROVIDER)) {
        mGpsProviderSupported = true;
        break;
      }
    }

    setupSpeedView();
    setupGps();
    updateGpsUi();
    checkPerms();

    mB.grantPerm.setOnClickListener(v -> Utils.openAppSettings(this, getPackageName()));

    if (GithubStar.shouldShowStarDialog()) GithubStar.starDialog(this,"https://github.com/woheller69/gpscockpit");
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
    if (!recording){
      stopTimer();
      stopLocListeners();
    }
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

  private void setupSpeedView(){
    mB.gpsCont.deluxeSpeedView.clearSections();

    mB.gpsCont.deluxeSpeedView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        int maxSpeedIndex = SETTINGS.getIntPref(R.string.pref_max_speed_index_key, defaultSpeedIndexList[SETTINGS.getIntPref(R.string.pref_units_key,METRIC)]);
        maxSpeedIndex = (maxSpeedIndex+1)%speedList.length;
        mB.gpsCont.deluxeSpeedView.setMaxSpeed(speedList[maxSpeedIndex]);
        SETTINGS.savePref(R.string.pref_max_speed_index_key,maxSpeedIndex);
      }
    });
    mB.gpsCont.deluxeSpeedView.addSections(
            new Section(.0f, 1.0f, ContextCompat.getColor(this, R.color.accent),10));
  }

  @Override
  public void onBackPressed() {
    if (gpsLocked) {
      moveTaskToBack(true);
    }
    else{
      if (VERSION.SDK_INT == VERSION_CODES.Q) {
         // Bug: https://issuetracker.google.com/issues/139738913
        finishAfterTransition();
      } else {
        super.onBackPressed();
      }
    }
  }

  @SuppressLint("RestrictedApi")
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    mMenu = menu;
    getMenuInflater().inflate(R.menu.main_overflow, menu);
    MenuCompat.setGroupDividerEnabled(menu, true);
    if (menu instanceof MenuBuilder) {
      ((MenuBuilder) menu).setOptionalIconsVisible(true);
    }
    menu.findItem(R.id.action_dark_theme).setChecked(SETTINGS.getForceDarkMode());

    int units = SETTINGS.getIntPref(R.string.pref_units_key,METRIC);
    if (units == METRIC){
      menu.findItem(R.id.action_units_metric).setChecked(true);
    } else if (units == IMPERIAL){
      menu.findItem(R.id.action_units_imperial).setChecked(true);
    } else if (units == NAUTICAL){
      menu.findItem(R.id.action_units_nautical).setChecked(true);
    }

    menu.findItem(R.id.action_lock_gps).setChecked(gpsLocked);
    menu.findItem(R.id.action_debug).setChecked(SETTINGS.getBoolPref(R.string.pref_debug_key,false));
    menu.findItem(R.id.action_compass_degrees).setChecked(SETTINGS.getBoolPref(R.string.pref_compass_key,false));
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int itemId = item.getItemId();
    if (itemId == R.id.action_dark_theme) {
      SETTINGS.setForceDarkMode(!item.isChecked());  // item.isChecked always previous value until invalidated, so value has to be inverted
      setNightTheme(this);
      invalidateOptionsMenu();
      return true;
    }

    if (item.getGroupId() == R.id.action_units_group){
      if (itemId == R.id.action_units_header) {
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW); //https://stackoverflow.com/questions/52176838/how-to-hold-the-overflow-menu-after-i-click-it/52177919#52177919
        item.setActionView(new View(this));
        item.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
          @Override
          public boolean onMenuItemActionExpand(MenuItem item) {
            mMenu.findItem(R.id.action_units_metric).setVisible(!mMenu.findItem(R.id.action_units_metric).isVisible());
            mMenu.findItem(R.id.action_units_imperial).setVisible(!mMenu.findItem(R.id.action_units_imperial).isVisible());
            mMenu.findItem(R.id.action_units_nautical).setVisible(!mMenu.findItem(R.id.action_units_nautical).isVisible());
            return false;
          }
          @Override
          public boolean onMenuItemActionCollapse(MenuItem item) {
            return false;
          }
        });
        return false;
      }
      if (!item.isChecked()){
        if (itemId == R.id.action_units_metric) SETTINGS.savePref(R.string.pref_units_key, METRIC);
        else if (itemId == R.id.action_units_imperial) SETTINGS.savePref(R.string.pref_units_key, IMPERIAL);
        else if (itemId == R.id.action_units_nautical) SETTINGS.savePref(R.string.pref_units_key, NAUTICAL);
      }
      SETTINGS.savePref(R.string.pref_max_speed_index_key, defaultSpeedIndexList[SETTINGS.getIntPref(R.string.pref_units_key,METRIC)]);
      updateGpsUi();
      invalidateOptionsMenu();
      return true;
    }

    if (itemId == R.id.action_compass_degrees) {
      SETTINGS.savePref(R.string.pref_compass_key, !item.isChecked());
      updateGpsUi();
      invalidateOptionsMenu();
      return true;
    }
    if (itemId == R.id.action_satview) {
      showSatsDialog();
      return true;
    }
    if (itemId == R.id.action_clear_agps) {
      clearAGPSData();
      return true;
    }
    if (itemId == R.id.action_lock_gps) {
      lockGPS(!item.isChecked());
      invalidateOptionsMenu();
      return true;
    }
    if (itemId == R.id.action_debug) {
      SETTINGS.savePref(R.string.pref_debug_key, !item.isChecked());
      updateGpsUi();
      invalidateOptionsMenu();
      return true;
    }
    if (itemId == R.id.action_about) {
      AboutDialogFragment.show(this);
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void lockGPS(boolean lock) {
    if (lock) {
      Intent intent = new Intent(App.getCxt(), GpsSvc.class);
      // If startForeground() in Service is called on UI thread, it won't show notification
      // unless Service is started with startForegroundService().
      if (SDK_INT >= VERSION_CODES.O) {
        if (!GpsSvc.mIsRunning) startForegroundService(intent);
        startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:"+getPackageName())));
      } else {
        if (!GpsSvc.mIsRunning) startService(intent);
        startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:"+getPackageName())));
      }
    } else {
      startService(new Intent(App.getCxt(), GpsSvc.class).setAction(ACTION_STOP_SERVICE));
    }
    gpsLocked=lock;
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

    mB.reset.setOnClickListener(v -> resetDistances());
    mB.record.setOnClickListener(v -> {
      recording=mB.record.isChecked();
      if (mB.record.isChecked()){
        if (gpsLocked) gpsLockedBeforeStart = true; //register if GPS was locked before "Start" already
        lockGPS(true);
      } else {
        if (!gpsLockedBeforeStart) lockGPS(false); //switch off GPS Service only if it was not locked before "Start"
        gpsLockedBeforeStart=false; //Reset to false when "Stop" is pressed
      }

      if (mB.record.isChecked()){
        mOldGpsLocation=null;  //reset old position when recording is started so counting continues from current position / altitude
        mNmeaOldAltitude=null;
        mStartTime=System.currentTimeMillis()/1000-(mEndTime-mStartTime);
        mEndTime=System.currentTimeMillis()/1000;
      }
      invalidateOptionsMenu();
      });

    mB.gpsCont.map.setOnClickListener(v -> openMap(this, mGpsLocation));
    mB.gpsCont.copy.setOnClickListener(v -> copyLoc(mGpsLocation));
    mB.gpsCont.share.setOnClickListener(v -> shareLoc(this, mGpsLocation));

    if (GpsSvc.mIsRunning) {
      gpsLocked=true;
      invalidateOptionsMenu();
    }

    Utils.setTooltip(mB.gpsCont.map);
    Utils.setTooltip(mB.gpsCont.copy);
    Utils.setTooltip(mB.gpsCont.share);
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
    if (!recording) {
      mGpsLocation = null;
      mNmeaAltitude = null;
    }

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

  private void updateUi() {
    if (gpsLocked!=GpsSvc.mIsRunning){
      gpsLocked=GpsSvc.mIsRunning;
      invalidateOptionsMenu();
    }
    if (mB != null) {
      updateGpsUi();
    }
  }

  private void updateGpsUi() {
    String state = null, lat = "--", lng = "--", acc = "--", altMSL = "--", dist = "--", up = "--", down = "--", speed_av = "--", speed_max = "--";
    boolean hasFineLocPerm = false, showSats = false, locAvailable = false;
    float bearing = 0;
    float speedval = -1;
    float speedAverage=0;
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
          updateDistance();

          lat = Utils.formatLatLng(mGpsLocation.getLatitude());
          lng = Utils.formatLatLng(mGpsLocation.getLongitude());
          mB.gpsCont.latV.setTextColor(ContextCompat.getColor(this,R.color.dynamicFgDim));
          mB.gpsCont.lngV.setTextColor(ContextCompat.getColor(this,R.color.dynamicFgDim));
          if (mNmeaAltitude!=null) {
            if (SETTINGS.getIntPref(R.string.pref_units_key, METRIC) == METRIC){
              altMSL = getString(R.string.dist_unit, Utils.formatInt(mNmeaAltitude ));
              mB.gpsCont.simpleClock.setAltitude(mNmeaAltitude);
            }else{
              altMSL = getString(R.string.dist_unit_imperial, Utils.formatInt(mNmeaAltitude*3.28084f)); //convert to feet
              mB.gpsCont.simpleClock.setAltitude(mNmeaAltitude*3.28084f);
            }
          }
          if (mGpsLocation.hasSpeed()) {
            if (SETTINGS.getIntPref(R.string.pref_units_key, METRIC) == METRIC){
              speedval = mGpsLocation.getSpeed() * 3.6f; //convert to km/h
            }else if (SETTINGS.getIntPref(R.string.pref_units_key, METRIC) == IMPERIAL){
              speedval = mGpsLocation.getSpeed() * 2.236936f; //convert to mph
            }else if (SETTINGS.getIntPref(R.string.pref_units_key, METRIC) == NAUTICAL){
              speedval = mGpsLocation.getSpeed() * 1.943844f; //convert to kn
            }
            mB.gpsCont.deluxeSpeedView.setSpeedTextColor(ContextCompat.getColor(this,R.color.dynamicFgDim));
          }else{
            mB.gpsCont.deluxeSpeedView.setSpeedTextColor(ContextCompat.getColor(this,R.color.disabledStateColor));
          }
          if (mGpsLocation.hasAccuracy()) {
            mB.gpsCont.accV.setTextColor(ContextCompat.getColor(this,R.color.dynamicFgDim));
            if (SETTINGS.getIntPref(R.string.pref_units_key, METRIC) == METRIC){
              acc = getString(R.string.dist_unit, Utils.formatLocAccuracy(mGpsLocation.getAccuracy()));
            } else {
              acc = getString(R.string.dist_unit_imperial, Utils.formatLocAccuracy(mGpsLocation.getAccuracy()*3.28084f));
            }
          }
          if (mGpsLocation.hasAltitude()) {
            mB.gpsCont.altitudeMSL.setTextColor(ContextCompat.getColor(this,R.color.dynamicFgDim));
            mB.gpsCont.simpleClock.setBackTint(ContextCompat.getColor(this,R.color.accent));
            mB.gpsCont.simpleClock.setHourTint(ContextCompat.getColor(this,R.color.primaryTrans));
            mB.gpsCont.simpleClock.setMinuteTint(ContextCompat.getColor(this,R.color.primaryTrans));
            mB.gpsCont.simpleClock.setSecondTint(ContextCompat.getColor(this,R.color.primaryTrans));
          } else {
            mB.gpsCont.altitudeMSL.setTextColor(ContextCompat.getColor(this,R.color.disabledStateColor));
            mB.gpsCont.simpleClock.setBackTint(ContextCompat.getColor(this,R.color.disabledStateColor));
            mB.gpsCont.simpleClock.setHourTint(ContextCompat.getColor(this,R.color.disabledStateColor));
            mB.gpsCont.simpleClock.setMinuteTint(ContextCompat.getColor(this,R.color.disabledStateColor));
            mB.gpsCont.simpleClock.setSecondTint(ContextCompat.getColor(this,R.color.disabledStateColor));
          }
          if (mGpsLocation.hasBearing()) {
            mB.gpsCont.compass.setLineColor(ContextCompat.getColor(this,R.color.accent));
            mB.gpsCont.compass.setTextColor(ContextCompat.getColor(this,R.color.dynamicFgDim));
            mB.gpsCont.compass.setShowMarker(true);
            bearing = mGpsLocation.getBearing();
          } else {
            mB.gpsCont.compass.setLineColor(ContextCompat.getColor(this,R.color.disabledStateColor));
            mB.gpsCont.compass.setTextColor(ContextCompat.getColor(this,R.color.disabledStateColor));
            mB.gpsCont.compass.setShowMarker(false);
          }
        }
      }
    }
    if (SETTINGS.getBoolPref(R.string.pref_debug_key,false)) {
      mB.gpsCont.debugCounter.setVisibility(View.VISIBLE);
    }    else {
      mB.gpsCont.debugCounter.setVisibility(View.GONE);
    }
    if (SETTINGS.getBoolPref(R.string.pref_compass_key,false)) {
      mB.gpsCont.compass.setRangeDegrees(50);  //show numbers
    }    else {
      mB.gpsCont.compass.setRangeDegrees(180);  //show N - NW - W ...
    }
    if (SETTINGS.getIntPref(R.string.pref_units_key, METRIC) == METRIC){
      mB.gpsCont.deluxeSpeedView.setUnit(getString(R.string.speed_unit));
    }else if (SETTINGS.getIntPref(R.string.pref_units_key, METRIC) == IMPERIAL){
      mB.gpsCont.deluxeSpeedView.setUnit(getString(R.string.speed_unit_imperial));
    }else if (SETTINGS.getIntPref(R.string.pref_units_key, METRIC) == NAUTICAL){
      mB.gpsCont.deluxeSpeedView.setUnit(getString(R.string.speed_unit_nautical));
    }
    mB.gpsCont.deluxeSpeedView.setMaxSpeed(speedList[SETTINGS.getIntPref(R.string.pref_max_speed_index_key,defaultSpeedIndexList[SETTINGS.getIntPref(R.string.pref_units_key,METRIC)])]);
    mB.reset.setEnabled(hasFineLocPerm);
    mB.record.setEnabled(hasFineLocPerm);
    mB.gpsCont.map.setEnabled(locAvailable);
    mB.gpsCont.copy.setEnabled(locAvailable);
    mB.gpsCont.share.setEnabled(locAvailable);
    mB.gpsCont.stateV.setText(state);
    mB.gpsCont.latV.setText(lat);
    mB.gpsCont.lngV.setText(lng);
    mB.gpsCont.altitudeMSL.setText(altMSL);
    mB.gpsCont.deluxeSpeedView.speedTo(speedval);
    mB.gpsCont.accV.setText(acc);
    if (recording) mEndTime = System.currentTimeMillis()/1000;
    String dateFormat = DateUtils.formatElapsedTime(mEndTime-mStartTime);
    mB.gpsCont.timeV.setText(dateFormat);

    if (SETTINGS.getIntPref(R.string.pref_units_key, METRIC) == METRIC){
      dist = getString(R.string.dist_unit2, Utils.formatFloat(mTravelDistance/1000f));
    }else if (SETTINGS.getIntPref(R.string.pref_units_key, METRIC) == IMPERIAL){
      dist = getString(R.string.dist_unit_imperial2, Utils.formatFloat(mTravelDistance/1000/1.609344f)); //convert to miles
    }else if (SETTINGS.getIntPref(R.string.pref_units_key, METRIC) == NAUTICAL){
      dist = getString(R.string.dist_unit_nautical2, Utils.formatFloat(mTravelDistance/1852f)); //convert to nautical miles
    }

    if (SETTINGS.getIntPref(R.string.pref_units_key, METRIC) == METRIC){
      up = getString(R.string.dist_unit, Utils.formatInt(mAltUp));
    }else{
      up = getString(R.string.dist_unit_imperial, Utils.formatInt(mAltUp*3.28084f)); //convert to feet
    }

    if (SETTINGS.getIntPref(R.string.pref_units_key, METRIC) == METRIC){
      down = getString(R.string.dist_unit, Utils.formatInt(mAltDown));
    }else{
      down = getString(R.string.dist_unit_imperial, Utils.formatInt(mAltDown*3.28084f)); //convert to feet
    }

    mB.gpsCont.dist.setText(dist);
    mB.gpsCont.distUp.setText(up + "\u2191");
    mB.gpsCont.distDown.setText(down + "\u2193" );

    if ((mEndTime-mStartTime)>0) {
      speedAverage = mTravelDistance / (mEndTime - mStartTime);  // im m/s
    }
    if (SETTINGS.getIntPref(R.string.pref_units_key, METRIC) == METRIC){
       speed_av = Utils.formatInt(speedAverage * 3.6f) + " " + getString(R.string.speed_unit); //convert to km/h
    }else if (SETTINGS.getIntPref(R.string.pref_units_key, METRIC) == IMPERIAL){
       speed_av = Utils.formatInt(speedAverage * 2.236936f) + " " + getString(R.string.speed_unit_imperial); //convert to mph
    }else if (SETTINGS.getIntPref(R.string.pref_units_key, METRIC) == NAUTICAL){
      speed_av = Utils.formatInt(speedAverage * 1.943844f) + " " + getString(R.string.speed_unit_nautical); //convert to kn
    }
    mB.gpsCont.speedAv.setText(speed_av);

    if (SETTINGS.getIntPref(R.string.pref_units_key, METRIC) == METRIC){
      speed_max = Utils.formatInt(mMaxSpeed * 3.6f) + " " + getString(R.string.speed_unit); //convert to km/h
    }else if (SETTINGS.getIntPref(R.string.pref_units_key, METRIC) == IMPERIAL){
      speed_max = Utils.formatInt(mMaxSpeed * 2.236936f) + " " + getString(R.string.speed_unit_imperial); //convert to mph
    }else if (SETTINGS.getIntPref(R.string.pref_units_key, METRIC) == NAUTICAL){
      speed_max = Utils.formatInt(mMaxSpeed * 1.943844f) + " " + getString(R.string.speed_unit_nautical); //convert to kn
    }
    mB.gpsCont.speedMax.setText(speed_max);

    if (mGpsLocation!=null && mGpsLocation.hasBearing()) mB.gpsCont.compass.setDegrees(bearing,true);
    mB.gpsCont.debugCounter.setText(Long.toString(mDebugCounter));

    if (mGpsLocation==null || (System.currentTimeMillis()-mGpsLocation.getTime())> 3*MIN_DELAY){
      mB.gpsCont.latV.setTextColor(ContextCompat.getColor(this,R.color.disabledStateColor));
      mB.gpsCont.lngV.setTextColor(ContextCompat.getColor(this,R.color.disabledStateColor));
      mB.gpsCont.accV.setTextColor(ContextCompat.getColor(this,R.color.disabledStateColor));
      mB.gpsCont.altitudeMSL.setTextColor(ContextCompat.getColor(this,R.color.disabledStateColor));
      mB.gpsCont.simpleClock.setBackTint(ContextCompat.getColor(this,R.color.disabledStateColor));
      mB.gpsCont.simpleClock.setHourTint(ContextCompat.getColor(this,R.color.disabledStateColor));
      mB.gpsCont.simpleClock.setMinuteTint(ContextCompat.getColor(this,R.color.disabledStateColor));
      mB.gpsCont.simpleClock.setSecondTint(ContextCompat.getColor(this,R.color.disabledStateColor));
      mB.gpsCont.deluxeSpeedView.setSpeedTextColor(ContextCompat.getColor(this,R.color.disabledStateColor));
    }

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

    @Deprecated
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras){
    }

    @Override
    public void onLocationChanged(Location location) {
      if (mIsGps) {
        mGpsLocation = location;
        if (recording) mDebugCounter++;
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

  private void resetDistances() {
    mNmeaOldAltitude=null;
    mTravelDistance=0;
    mAltUp=0d;
    mAltDown=0d;
    mMaxSpeed=0f;
    mStartTime=System.currentTimeMillis()/1000;
    mEndTime=System.currentTimeMillis()/1000;
    mDebugCounter=0;
  }

  private void updateDistance() {
    if (!recording) return;

    double latitude = mGpsLocation.getLatitude();
    double longitude = mGpsLocation.getLongitude();

    float min_accuracy = 15; // min Accuracy must be 15m

    if (mGpsLocation.hasAccuracy() && mGpsLocation.getAccuracy() < min_accuracy) {   // Only calculate distance if accuracy is high

      if (mOldGpsLocation==null || mNmeaOldAltitude==null) {  //if this is the first position only store it and reset travel distance
        if (mGpsLocation!=null) mOldGpsLocation = mGpsLocation;
        if (mNmeaAltitude!=null) mNmeaOldAltitude = mNmeaAltitude;
      } else {
        // The distance in meters is the first element returned from distanceBetween().
        float[] distance = new float[1];
        Location.distanceBetween(mOldGpsLocation.getLatitude(), mOldGpsLocation
                .getLongitude(), latitude, longitude, distance);
          if (distance[0] > 2*mGpsLocation.getAccuracy()) { // Update the total of travel distance only if distance is larger than 2x accuracy.
            mTravelDistance += distance[0];
            mOldGpsLocation = mGpsLocation;
          }
          if (mNmeaAltitude!=null) {
            if ((mNmeaAltitude - mNmeaOldAltitude) > 3 * mGpsLocation.getAccuracy()) {
              mAltUp = mAltUp + mNmeaAltitude - mNmeaOldAltitude;
              mNmeaOldAltitude = mNmeaAltitude;
            } else if ((mNmeaOldAltitude - mNmeaAltitude) > 3 * mGpsLocation.getAccuracy()) {
              mAltDown = mAltDown + mNmeaOldAltitude - mNmeaAltitude;
              mNmeaOldAltitude = mNmeaAltitude;
            }
          }
        if (mGpsLocation.hasSpeed() && mGpsLocation.getSpeed()>mMaxSpeed) mMaxSpeed=mGpsLocation.getSpeed();
      }
    }
  }

}
