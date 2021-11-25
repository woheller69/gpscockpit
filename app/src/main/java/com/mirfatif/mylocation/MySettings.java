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

  public void plusAppLaunchCount() {
    int appLaunchCountId = R.string.pref_main_app_launch_count_for_feedback_key;
    savePref(appLaunchCountId, getIntPref(appLaunchCountId, 0) + 1);
  }

  public boolean shouldAskForFeedback() {
    long lastTS = getLongPref(R.string.pref_main_ask_for_feedback_ts_key);
    if (lastTS == 0) {
      setAskForFeedbackTs(System.currentTimeMillis());
      return false;
    }
    int appLaunchCountId = R.string.pref_main_app_launch_count_for_feedback_key;
    boolean ask = getIntPref(appLaunchCountId, 0) >= 10;
    ask = ask && (System.currentTimeMillis() - lastTS) >= TimeUnit.DAYS.toMillis(10);
    if (ask) {
      savePref(appLaunchCountId, 0);
      setAskForFeedbackTs(System.currentTimeMillis());
    }
    return ask;
  }

  public void setAskForFeedbackTs(long ts) {
    savePref(R.string.pref_main_ask_for_feedback_ts_key, ts);
  }
}
