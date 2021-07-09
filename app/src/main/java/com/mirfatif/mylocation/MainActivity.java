package com.mirfatif.mylocation;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.location.LocationManager.GPS_PROVIDER;
import static android.location.LocationManager.NETWORK_PROVIDER;
import static android.os.Build.VERSION.SDK_INT;
import static com.mirfatif.mylocation.GpsSvc.ACTION_STOP_SERVICE;
import static com.mirfatif.mylocation.GpsSvc.MIN_DELAY;
import static com.mirfatif.mylocation.MySettings.SETTINGS;
import static com.mirfatif.mylocation.Utils.copyLoc;
import static com.mirfatif.mylocation.Utils.hasCoarseLocPerm;
import static com.mirfatif.mylocation.Utils.hasFineLocPerm;
import static com.mirfatif.mylocation.Utils.isNaN;
import static com.mirfatif.mylocation.Utils.openMap;
import static com.mirfatif.mylocation.Utils.setNightTheme;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.app.ActivityCompat;
import androidx.core.view.MenuCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.mirfatif.mylocation.NlpAdapter.NlpClickListener;
import com.mirfatif.mylocation.databinding.ActivityMainBinding;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends AppCompatActivity {

  private ActivityMainBinding mB;

  private final LocationManager mLocManager =
      (LocationManager) App.getCxt().getSystemService(Context.LOCATION_SERVICE);

  private LicenseChecker mLicenseChecker;
  private boolean mGpsProviderSupported = false;
  private boolean mNetProviderSupported = false;

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
      actionBar.setIcon(R.drawable.action_bar_icon);
    }

    for (String provider : mLocManager.getAllProviders()) {
      if (provider.equals(GPS_PROVIDER)) {
        mGpsProviderSupported = true;
      }
      if (provider.equals(NETWORK_PROVIDER)) {
        mNetProviderSupported = true;
      }
    }

    setupGps();
    updateGpsUi();
    setupNetwork();
    updateNetUi();
    setupUnifiedNlp();
    checkPerms();

    mB.grantPerm.setOnClickListener(v -> Utils.openAppSettings(this, getPackageName()));

    mLicenseChecker = new LicenseChecker(this);
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
    checkLicense();
  }

  @Override
  protected void onDestroy() {
    if (mLicenseChecker != null) {
      mLicenseChecker.onDestroy();
    }
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
    if (BuildConfig.IS_PS) {
      menu.findItem(R.id.action_donate).setVisible(false);
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int itemId = item.getItemId();
    if (itemId == R.id.action_loc_settings) {
      try {
        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
      } catch (ActivityNotFoundException ignored) {
        Utils.showToast(R.string.failed_open_loc_settings);
      }
      return true;
    }
    if (itemId == R.id.action_dark_theme) {
      SETTINGS.setForceDarkMode(!item.isChecked());
      setNightTheme(this);
      return true;
    }
    if (itemId == R.id.action_donate) {
      new DonateDialogFragment().show(getSupportFragmentManager(), "DONATE");
      return true;
    }
    if (itemId == R.id.action_about) {
      new AboutDialogFragment().showNow(getSupportFragmentManager(), "ABOUT_DIALOG");
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  //////////////////////////////////////////////////////////////////
  ////////////////////////// LOC PROVIDERS /////////////////////////
  //////////////////////////////////////////////////////////////////

  private void setupGps() {
    if (!mGpsProviderSupported) {
      return;
    }

    mB.clearAgps.setOnClickListener(v -> clearAGPSData());
    mB.lockGps.setOnClickListener(
        v -> {
          if (mB.lockGps.isChecked()) {
            Intent intent = new Intent(App.getCxt(), GpsSvc.class);
            // If startForeground() in Service is called on UI thread, it won't show notification
            // unless Service is started with startForegroundService().
            if (SDK_INT >= VERSION_CODES.O) {
              startForegroundService(intent);
            } else {
              startService(intent);
            }
          } else {
            startService(new Intent(App.getCxt(), GpsSvc.class).setAction(ACTION_STOP_SERVICE));
          }
        });

    mB.gpsCont.map.setOnClickListener(v -> openMap(this, mGpsLocation));
    mB.gpsCont.copy.setOnClickListener(v -> copyLoc(mGpsLocation));

    // setOnCheckedChangeListener() doesn't work well on screen rotation.
    mB.gpsCont.switchV.setOnClickListener(
        v -> {
          if (SETTINGS.getGpsEnabled() != mB.gpsCont.switchV.isChecked()) {
            SETTINGS.setGpsEnabled(mB.gpsCont.switchV.isChecked());
            startGpsLocListener();
            setTimer();
          }
        });

    if (GpsSvc.mIsRunning) {
      mB.lockGps.setChecked(true);
    }

    mB.gpsCont.satDetail.setOnClickListener(v -> showSatsDialog());

    Utils.setTooltip(mB.gpsCont.map);
    Utils.setTooltip(mB.gpsCont.copy);
    Utils.setTooltip(mB.gpsCont.satDetail);
  }

  private void setupNetwork() {
    if (!mNetProviderSupported) {
      return;
    }

    mB.netCont.map.setOnClickListener(v -> openMap(this, mNetLocation));
    mB.netCont.copy.setOnClickListener(v -> copyLoc(mNetLocation));

    mB.netCont.switchV.setOnClickListener(
        v -> {
          if (SETTINGS.getNetworkEnabled() != mB.netCont.switchV.isChecked()) {
            SETTINGS.setNetworkEnabled(mB.netCont.switchV.isChecked());
            startNetLocListener();
            setTimer();
          }
        });

    Utils.setTooltip(mB.netCont.map);
    Utils.setTooltip(mB.netCont.copy);
  }

  private static final String ACTION_LOCATION_BACKEND = "org.microg.nlp.LOCATION_BACKEND";

  private final List<NlpBackend> mBackends = new ArrayList<>();
  private NlpAdapter mNlpAdapter;

  private void setupUnifiedNlp() {
    Intent intent = new Intent(ACTION_LOCATION_BACKEND);
    List<ResolveInfo> infoList = getPackageManager().queryIntentServices(intent, 0);
    synchronized (mBackends) {
      mBackends.clear();
      for (ResolveInfo info : infoList) {
        mBackends.add(new NlpBackend(info.serviceInfo));
      }
    }

    if (infoList.size() == 0) {
      mB.nlpCont.stateV.setText(R.string.not_installed);
    }
    mB.nlpCont.switchV.setOnClickListener(
        v -> {
          if (SETTINGS.getNlpEnabled() != mB.nlpCont.switchV.isChecked()) {
            SETTINGS.setNlpEnabled(mB.nlpCont.switchV.isChecked());
            startNlpBackends();
            setTimer();
          }
        });

    mNlpAdapter =
        new NlpAdapter(
            new NlpClickListener() {
              @Override
              public void mapClicked(Location loc) {
                openMap(MainActivity.this, loc);
              }

              @Override
              public void copyClicked(Location loc) {
                copyLoc(loc);
              }

              @Override
              public void settingsClicked(String pkg) {
                Utils.openAppSettings(MainActivity.this, pkg);
              }
            },
            mBackends);
    mB.nlpCont.rv.setAdapter(mNlpAdapter);
    mB.nlpCont.rv.setLayoutManager(new LinearLayoutManager(this));
    mB.nlpCont.rv.addItemDecoration(
        new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
  }

  private final Object LOC_LISTENER_LOCK = new Object();

  private void startLocListeners() {
    startGpsLocListener();
    startNetLocListener();
    startNlpBackends();
  }

  private LocListener mGpsLocListener;
  private GpsStatus.Listener mGpsStatusListener;

  @SuppressLint("MissingPermission")
  private void startGpsLocListener() {
    synchronized (LOC_LISTENER_LOCK) {
      stopGpsLocListener();
      if (SETTINGS.getGpsEnabled() && mGpsProviderSupported && hasFineLocPerm()) {
        mGpsLocListener = new LocListener(true);
        mLocManager.requestLocationUpdates(GPS_PROVIDER, MIN_DELAY, 0, mGpsLocListener);
        mGpsStatusListener = new GpsStatusListener();
        mLocManager.addGpsStatusListener(mGpsStatusListener);
      }
    }
  }

  private LocListener mNetLocListener;

  @SuppressLint("MissingPermission")
  private void startNetLocListener() {
    synchronized (LOC_LISTENER_LOCK) {
      stopNetLocListener();
      if (SETTINGS.getNetworkEnabled()
          && mNetProviderSupported
          && (hasCoarseLocPerm() || hasFineLocPerm())) {
        mNetLocListener = new LocListener(false);
        mLocManager.requestLocationUpdates(NETWORK_PROVIDER, MIN_DELAY, 0, mNetLocListener);
      }
    }
  }

  private void startNlpBackends() {
    synchronized (mBackends) {
      stopNlpBackends();
      if (SETTINGS.getNlpEnabled()) {
        for (NlpBackend backend : mBackends) {
          backend.start();
        }
      }
    }
  }

  private void stopLocListeners() {
    stopGpsLocListener();
    stopNetLocListener();
    stopNlpBackends();
  }

  private void stopGpsLocListener() {
    synchronized (LOC_LISTENER_LOCK) {
      if (mGpsLocListener != null) {
        mLocManager.removeUpdates(mGpsLocListener);
        mGpsLocListener = null;
      }
      if (mGpsStatusListener != null) {
        mLocManager.removeGpsStatusListener(mGpsStatusListener);
        mGpsStatusListener = null;
      }
      clearGpsData();
    }
  }

  private void clearGpsData() {
    mGpsLocation = null;
    synchronized (mSats) {
      mSats.clear();
    }
  }

  private void stopNetLocListener() {
    synchronized (LOC_LISTENER_LOCK) {
      if (mNetLocListener != null) {
        mLocManager.removeUpdates(mNetLocListener);
        mNetLocListener = null;
      }
      mNetLocation = null;
    }
  }

  private void stopNlpBackends() {
    synchronized (mBackends) {
      for (NlpBackend backend : mBackends) {
        backend.stop();
      }
    }
  }

  //////////////////////////////////////////////////////////////////
  /////////////////////////////// UI //////////////////////////////
  //////////////////////////////////////////////////////////////////

  private final List<Sat> mSats = new ArrayList<>();
  private final ReentrantLock UPDATE_SATS_LOCK = new ReentrantLock();

  @SuppressLint("MissingPermission")
  private void updateGpsSats() {
    if (!UPDATE_SATS_LOCK.tryLock()) {
      return;
    }
    if (hasFineLocPerm()) {
      GpsStatus gpsStatus = mLocManager.getGpsStatus(null);
      synchronized (mSats) {
        mSats.clear();
        for (GpsSatellite gpsSat : gpsStatus.getSatellites()) {
          mSats.add(new Sat(gpsSat.getPrn(), gpsSat.usedInFix(), gpsSat.getSnr()));
        }
        Collections.sort(mSats, (s1, s2) -> Float.compare(s2.mSnr, s1.mSnr));
      }
    }
    UPDATE_SATS_LOCK.unlock();
  }

  private Timer mTimer;
  private long mPeriod = 1000;
  private int mTickCount;

  private void setTimer() {
    mPeriod = 1000;
    mTickCount = 0;
    startTimer();
  }

  private void startTimer() {
    stopTimer();
    mTimer = new Timer();
    mTimer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            Utils.runUi(() -> updateUi());
            mTickCount++;
            if (mTickCount == 5) {
              mPeriod = 5000;
              startTimer();
            }
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

  private Location mGpsLocation, mNetLocation;

  private void updateUi() {
    if (mB != null && mLicenseChecker != null && mLicenseChecker.isVerified()) {
      updateGpsUi();
      updateNetUi();
      updateNlpUi();
    }
  }

  private void updateGpsUi() {
    String state = null, lat = "--", lng = "--", acc = "--", time = "--";
    boolean hasFineLocPerm = false, showSats = false, locAvailable = false;
    if (!mGpsProviderSupported) {
      state = getString(R.string.not_supported);
    } else {
      hasFineLocPerm = hasFineLocPerm();
      if (!hasFineLocPerm) {
        state = getString(R.string.perm_not_granted);
      } else if (!mLocManager.isProviderEnabled(GPS_PROVIDER)) {
        state = getString(R.string.turned_off);
      } else {
        showSats = SETTINGS.getGpsEnabled();
        if (mGpsLocation != null
            && !isNaN(mGpsLocation.getLatitude())
            && !isNaN(mGpsLocation.getLongitude())) {
          locAvailable = true;
          lat = Utils.formatLatLng(mGpsLocation.getLatitude());
          lng = Utils.formatLatLng(mGpsLocation.getLongitude());
          if (!isNaN(mGpsLocation.getAccuracy()) && mGpsLocation.getAccuracy() != 0) {
            acc = getString(R.string.acc_unit, Utils.formatLocAccuracy(mGpsLocation.getAccuracy()));
          }
          long curr = System.currentTimeMillis();
          long t = mGpsLocation.getTime();
          t = t - Math.max(0, t - curr);
          time = DateUtils.getRelativeTimeSpanString(t).toString();
        }
      }
    }
    mB.clearAgps.setEnabled(hasFineLocPerm);
    mB.lockGps.setEnabled(hasFineLocPerm);
    mB.gpsCont.map.setEnabled(locAvailable);
    mB.gpsCont.copy.setEnabled(locAvailable);
    mB.gpsCont.switchV.setEnabled(hasFineLocPerm);
    mB.gpsCont.switchV.setChecked(hasFineLocPerm && SETTINGS.getGpsEnabled());
    mB.gpsCont.stateV.setText(state);
    mB.gpsCont.latV.setText(lat);
    mB.gpsCont.lngV.setText(lng);
    mB.gpsCont.accV.setText(acc);
    mB.gpsCont.timeV.setText(time);
    mB.gpsCont.satDetail.setEnabled(showSats);
    if (!showSats && mSatsDialog != null) {
      mSatsDialog.dismissAllowingStateLoss();
    }

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
        mSatsDialog.submitList(mSats);
      }
    }
  }

  private void updateNetUi() {
    String state = null, lat = "--", lng = "--", acc = "--", time = "--";
    boolean hasLocPerm = false, locAvailable = false;
    if (!mNetProviderSupported) {
      state = getString(R.string.not_supported);
    } else {
      hasLocPerm = hasCoarseLocPerm() || hasFineLocPerm();
      if (!hasLocPerm) {
        state = getString(R.string.perm_not_granted);
      } else if (!mLocManager.isProviderEnabled(NETWORK_PROVIDER)) {
        state = getString(R.string.turned_off);
      } else if (mNetLocation != null
          && !isNaN(mNetLocation.getLatitude())
          && !isNaN(mNetLocation.getLongitude())) {
        locAvailable = true;
        lat = Utils.formatLatLng(mNetLocation.getLatitude());
        lng = Utils.formatLatLng(mNetLocation.getLongitude());
        if (!isNaN(mNetLocation.getAccuracy()) && mNetLocation.getAccuracy() != 0) {
          acc = getString(R.string.acc_unit, Utils.formatLocAccuracy(mNetLocation.getAccuracy()));
        }
        long curr = System.currentTimeMillis();
        long t = mNetLocation.getTime();
        t = t - Math.max(0, t - curr);
        time = DateUtils.getRelativeTimeSpanString(t).toString();
      }
    }
    mB.netCont.map.setEnabled(locAvailable);
    mB.netCont.copy.setEnabled(locAvailable);
    mB.netCont.switchV.setEnabled(hasLocPerm);
    mB.netCont.switchV.setChecked(hasLocPerm && SETTINGS.getNetworkEnabled());
    mB.netCont.stateV.setText(state);
    mB.netCont.latV.setText(lat);
    mB.netCont.lngV.setText(lng);
    mB.netCont.accV.setText(acc);
    mB.netCont.timeV.setText(time);
  }

  private void updateNlpUi() {
    boolean hasLocPerm = hasCoarseLocPerm();
    mB.nlpCont.switchV.setEnabled(hasLocPerm);
    mB.nlpCont.switchV.setChecked(hasLocPerm && SETTINGS.getNlpEnabled());
    synchronized (mBackends) {
      for (NlpBackend backend : mBackends) {
        backend.refresh();
        if (mNlpAdapter != null) {
          mNlpAdapter.notifyDataSetChanged();
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

  void checkLicense() {
    if (mLicenseChecker != null) {
      mLicenseChecker.check();
    }
  }

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
      } else {
        mNetLocation = location;
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
      } else {
        mNetLocation = null;
      }
      setTimer();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
      setTimer();
    }
  }

  private class GpsStatusListener implements GpsStatus.Listener {

    @Override
    public void onGpsStatusChanged(int event) {
      Utils.runBg(MainActivity.this::updateGpsSats);
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
}
