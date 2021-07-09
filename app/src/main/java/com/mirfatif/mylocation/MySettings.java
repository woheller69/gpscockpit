package com.mirfatif.mylocation;

import static com.mirfatif.mylocation.Utils.getString;

import android.content.SharedPreferences;
import java.util.concurrent.TimeUnit;

public class MySettings {

  public static final MySettings SETTINGS = new MySettings();

  private final SharedPreferences mPrefs;

  private MySettings() {
    mPrefs = Utils.getDefPrefs();
  }

  public boolean getBoolPref(int keyResId, boolean defValue) {
    String prefKey = getString(keyResId);
    return mPrefs.getBoolean(prefKey, defValue);
  }

  public int getIntPref(int keyResId, int defValue) {
    String prefKey = getString(keyResId);
    return mPrefs.getInt(prefKey, defValue);
  }

  @SuppressWarnings("SameParameterValue")
  private long getLongPref(int keyResId) {
    String prefKey = getString(keyResId);
    return mPrefs.getLong(prefKey, 0);
  }

  public void savePref(int key, boolean bool) {
    String prefKey = getString(key);
    mPrefs.edit().putBoolean(prefKey, bool).apply();
  }

  public void savePref(int key, int integer) {
    String prefKey = getString(key);
    mPrefs.edit().putInt(prefKey, integer).apply();
  }

  @SuppressWarnings("SameParameterValue")
  private void savePref(int key, long _long) {
    String prefKey = getString(key);
    mPrefs.edit().putLong(prefKey, _long).apply();
  }

  public boolean getGpsEnabled() {
    return mPrefs.getBoolean(getString(R.string.pref_main_gps_enabled_key), true);
  }

  public void setGpsEnabled(boolean enabled) {
    mPrefs.edit().putBoolean(getString(R.string.pref_main_gps_enabled_key), enabled).apply();
  }

  public boolean getNetworkEnabled() {
    return mPrefs.getBoolean(getString(R.string.pref_main_network_enabled_key), true);
  }

  public void setNetworkEnabled(boolean enabled) {
    mPrefs.edit().putBoolean(getString(R.string.pref_main_network_enabled_key), enabled).apply();
  }

  public boolean getNlpEnabled() {
    return mPrefs.getBoolean(getString(R.string.pref_main_nlp_enabled_key), true);
  }

  public void setNlpEnabled(boolean enabled) {
    mPrefs.edit().putBoolean(getString(R.string.pref_main_nlp_enabled_key), enabled).apply();
  }

  public boolean shouldAskToSendCrashReport() {
    int crashCount = getIntPref(R.string.pref_main_crash_report_count_key, 1);
    long lastTS = getLongPref(R.string.pref_main_crash_report_ts_key);
    long currTime = System.currentTimeMillis();

    if (crashCount >= 5 || (currTime - lastTS) >= TimeUnit.DAYS.toMillis(1)) {
      savePref(R.string.pref_main_crash_report_ts_key, currTime);
      savePref(R.string.pref_main_crash_report_count_key, 1);
      return true;
    }

    savePref(R.string.pref_main_crash_report_count_key, crashCount + 1);
    return false;
  }

  public boolean getForceDarkMode() {
    return getBoolPref(R.string.pref_main_dark_theme_key, true);
  }

  public void setForceDarkMode(boolean force) {
    savePref(R.string.pref_main_dark_theme_key, force);
  }
}
