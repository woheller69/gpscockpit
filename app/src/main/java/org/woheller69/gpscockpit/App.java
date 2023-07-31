package org.woheller69.gpscockpit;

import static org.woheller69.gpscockpit.MySettings.SETTINGS;
import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import androidx.appcompat.app.AppCompatDelegate;
import java.io.PrintWriter;
import java.io.StringWriter;

public class App extends Application {

  private static final String TAG = "App";

  private static Context mAppContext;
  private Thread.UncaughtExceptionHandler defaultExceptionHandler;

  @Override
  public void onCreate() {
    super.onCreate();
    mAppContext = getApplicationContext();
    updateContext();
    defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();

      // This fixes the color of the ActionBar on some devices
      if (SETTINGS.getForceDarkMode()) { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);}
      else {
          AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
      }
    Thread.setDefaultUncaughtExceptionHandler(
        (t, e) -> {
          Log.e(TAG, e.toString());

          StringWriter stringWriter = new StringWriter();
          PrintWriter writer = new PrintWriter(stringWriter, true);
          e.printStackTrace(writer);
          writer.close();
          Utils.writeCrashLog(stringWriter.toString());

          defaultExceptionHandler.uncaughtException(t, e);
        });
  }

  public static void updateContext() {
    mAppContext = Utils.setLocale(mAppContext);
  }

  public static Context getCxt() {
    return mAppContext;
  }

  public static Resources getRes() {
    return mAppContext.getResources();
  }

}
