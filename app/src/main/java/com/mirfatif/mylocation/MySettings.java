package com.mirfatif.mylocation;

import static com.mirfatif.mylocation.Utils.getString;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import java.util.concurrent.TimeUnit;

public enum MySettings {
  SETTINGS;

  private final SharedPreferences mPrefs = Utils.getDefPrefs();

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

  @SuppressLint("ApplySharedPref")
  public boolean shouldAskToSendCrashReport() {
    int crashCount = getIntPref(R.string.pref_main_crash_report_count_key, 1);
    long lastTS = getLongPref(R.string.pref_main_crash_report_ts_key);
    long currTime = System.currentTimeMillis();

    Editor prefEditor = mPrefs.edit();
    try {
      if (crashCount >= 5 || (currTime - lastTS) >= TimeUnit.DAYS.toMillis(1)) {
        prefEditor.putLong(getString(R.string.pref_main_crash_report_ts_key), currTime);
        prefEditor.putInt(getString(R.string.pref_main_crash_report_count_key), 1);
        return true;
      }

      prefEditor.putInt(getString(R.string.pref_main_crash_report_count_key), crashCount + 1);
      return false;
    } finally {
      prefEditor.commit();
    }
  }

  public boolean getForceDarkMode() {
    return getBoolPref(R.string.pref_main_dark_theme_key, true);
  }

  public void setForceDarkMode(boolean force) {
    savePref(R.string.pref_main_dark_theme_key, force);
  }

  public String getLocale() {
    return mPrefs.getString(getString(R.string.pref_main_locale_key), "");
  }

  public void setLocale(String langCode) {
    mPrefs.edit().putString(getString(R.string.pref_main_locale_key), langCode).apply();
  }

}
