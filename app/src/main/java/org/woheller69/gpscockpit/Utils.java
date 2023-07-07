package org.woheller69.gpscockpit;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static org.woheller69.gpscockpit.MySettings.SETTINGS;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Window;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationManagerCompat;

import androidx.lifecycle.Lifecycle.State;
import androidx.lifecycle.LifecycleOwner;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class Utils {

  private static final String TAG = "Utils";

  private Utils() {}

  public static String getString(int resId, Object... args) {
    return App.getCxt().getString(resId, args);
  }

  public static int getInteger(int resId) {
    return App.getCxt().getResources().getInteger(resId);
  }

  public static boolean isNaN(double d) {
    return d != d;
  }

  public static void copyLoc(Location location) {
    if (location != null) {
      ClipboardManager clipboard =
          (ClipboardManager) App.getCxt().getSystemService(Context.CLIPBOARD_SERVICE);
      String loc = location.getLatitude() + "," + location.getLongitude();
      ClipData data = ClipData.newPlainText("location", loc);
      clipboard.setPrimaryClip(data);
      Utils.showShortToast(R.string.copied);
    }
  }

  public static void shareLoc(Activity act, Location location) {
    if (location != null) {
      String uri = "https://www.openstreetmap.org/?mlat="+location.getLatitude()+"&mlon="+location.getLongitude()+"#map=16/"+location.getLatitude()+"/"+location.getLongitude();
      Intent sharingIntent = new Intent(Intent.ACTION_SEND);
      sharingIntent.setType("text/plain");
      sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, uri);
      act.startActivity(Intent.createChooser(sharingIntent, "Share in..."));

    }
  }

  public static void openMap(Activity act, Location location) {
    if (location != null) {
      String loc = location.getLatitude() + "," + location.getLongitude();
      try {
        act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + loc + "?q=" + loc)));
      } catch (ActivityNotFoundException ignored) {
        Utils.showToast(R.string.no_maps_installed);
      }
    }
  }

  public static void openAppSettings(Activity act, String pkg) {
    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
    intent.setData(Uri.parse("package:" + pkg));
    try {
      act.startActivity(intent);
    } catch (ActivityNotFoundException ignored) {
      Utils.showToast(R.string.failed_open_app_settings);
    }
  }

  //////////////////////////////////////////////////////////////////
  /////////////////////////// FORMATTING ///////////////////////////
  //////////////////////////////////////////////////////////////////

  private static final DecimalFormat sLatLngFormat = new DecimalFormat();
  private static final DecimalFormat intFormat = new DecimalFormat("0");
  private static final DecimalFormat floatFormat = new DecimalFormat("0.00");

  static {
    sLatLngFormat.setMaximumFractionDigits(5);
  }

  public static String formatInt(double value) {
    intFormat.setRoundingMode(RoundingMode.HALF_UP);
    return intFormat.format(value).replaceAll("^-(?=0(\\.0*)?$)", "");  //remove the minus sign if it's followed by 0-n characters of "0.00000..."
  }

  public static String formatFloat(double value) {
    floatFormat.setRoundingMode(RoundingMode.HALF_UP);
    return floatFormat.format(value).replaceAll("^-(?=0(\\.0*)?$)", "");  //remove the minus sign if it's followed by 0-n characters of "0.00000..."
  }

  public static String formatLatLng(double coordinate) {
    return sLatLngFormat.format(coordinate);
  }

  public static String formatLocAccuracy(float accuracy) {
    return String.format(Locale.getDefault(), "%.0f", accuracy);
  }

  public static String getDeviceInfo() {
    return "Version: "
        + BuildConfig.VERSION_NAME
        + "\nSDK: "
        + VERSION.SDK_INT
        + "\nROM: "
        + Build.DISPLAY
        + "\nBuild: "
        + Build.TYPE
        + "\nDevice: "
        + Build.DEVICE
        + "\nManufacturer: "
        + Build.MANUFACTURER
        + "\nModel: "
        + Build.MODEL
        + "\nProduct: "
        + Build.PRODUCT;
  }

  public static String getCurrDateTime(boolean spaced) {
    if (spaced) {
      return new SimpleDateFormat("dd-MMM-yy HH:mm:ss", Locale.ENGLISH)
          .format(System.currentTimeMillis());
    } else {
      return new SimpleDateFormat("dd-MMM-yy_HH-mm-ss", Locale.ENGLISH)
          .format(System.currentTimeMillis());
    }
  }


  //////////////////////////////////////////////////////////////////
  ////////////////////////// PERMS / PREFS /////////////////////////
  //////////////////////////////////////////////////////////////////

  public static boolean hasFineLocPerm() {
    return hasPerm(ACCESS_FINE_LOCATION);
  }

  public static boolean hasCoarseLocPerm() {
    return hasPerm(ACCESS_COARSE_LOCATION);
  }

  public static boolean hasPerm(String perm) {
    return ActivityCompat.checkSelfPermission(App.getCxt(), perm) == PERMISSION_GRANTED;
  }

  @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
  public static boolean hasNotifPerm() {
    return hasPerm(POST_NOTIFICATIONS);
  }

  public static SharedPreferences getDefPrefs() {
    return App.getCxt().getSharedPreferences("def_prefs", Context.MODE_PRIVATE);
  }

  public static Context setLocale(Context context) {
    String lang = SETTINGS.getLocale();
    Locale locale;
    if (TextUtils.isEmpty(lang)) {
       locale = Resources.getSystem().getConfiguration().getLocales().get(0);
    } else {
      String[] langSpecs = lang.split("\\|");
      if (langSpecs.length == 2) {
        locale = new Locale(langSpecs[0], langSpecs[1]);
      } else {
        locale = new Locale(lang);
      }
    }
    Locale.setDefault(locale);
    Configuration config = context.getResources().getConfiguration();
    config.setLocale(locale);
    return context.createConfigurationContext(config);
  }

  //////////////////////////////////////////////////////////////////
  //////////////////////////// EXECUTORS ///////////////////////////
  //////////////////////////////////////////////////////////////////

  private static final Handler UI_EXECUTOR = new Handler(Looper.getMainLooper());

  @SuppressWarnings("UnusedReturnValue")
  public static UiRunnable runUi(LifecycleOwner lifecycleOwner, Runnable runnable) {
    if (lifecycleOwner.getLifecycle().getCurrentState().isAtLeast(State.INITIALIZED)) {
      return runUi(runnable);
    }
    return new UiRunnable();
  }

  public static UiRunnable runUi(Runnable runnable) {
    UiRunnable uiRunnable = new UiRunnable(runnable);
    UI_EXECUTOR.post(uiRunnable);
    return uiRunnable;
  }

  public static class UiRunnable implements Runnable {

    private final Runnable mRunnable;

    UiRunnable(Runnable runnable) {
      mRunnable = runnable;
    }

    UiRunnable() {
      mRunnable = null;
      mDone = true;
    }

    @Override
    public void run() {
      Objects.requireNonNull(mRunnable).run();
      mDone = true;
      synchronized (WAITER) {
        WAITER.notify();
      }
    }

    private boolean mDone = false;
    private final Object WAITER = new Object();

    @SuppressWarnings("UnusedDeclaration")
    public void waitForMe() {
      if (Thread.currentThread() == UI_EXECUTOR.getLooper().getThread()) {
        Log.e(TAG, "UiRunnable: waitForMe() called on main thread");
        return;
      }
      synchronized (WAITER) {
        while (!mDone) {
          try {
            WAITER.wait();
          } catch (InterruptedException ignored) {
          }
        }
      }
    }
  }

  private static final ExecutorService BG_EXECUTOR = Executors.newCachedThreadPool();

  public static Future<?> runBg(Runnable runnable) {
    return BG_EXECUTOR.submit(runnable);
  }

  //////////////////////////////////////////////////////////////////
  /////////////////////////////// UI ///////////////////////////////
  //////////////////////////////////////////////////////////////////

  public static void showToast(String msg) {
    if (msg != null) {
      runUi(() -> showToast(msg, Toast.LENGTH_LONG));
    }
  }

  public static void showToast(int resId, Object... args) {
    if (resId != 0) {
      showToast(getString(resId, args));
    }
  }

  public static void showShortToast(int resId, Object... args) {
    if (resId != 0) {
      runUi(() -> showToast(getString(resId, args), Toast.LENGTH_SHORT));
    }
  }

  private static void showToast(String msg, int duration) {
    Toast toast = Toast.makeText(App.getCxt(), msg, duration);
    toast.show();
  }

  public static void createNotifChannel(String id, String name, int importance) {
    NotificationManagerCompat nm = NotificationManagerCompat.from(App.getCxt());
    NotificationChannelCompat ch = nm.getNotificationChannelCompat(id);
    if (ch == null) {
      ch = new NotificationChannelCompat.Builder(id, importance).setName(name).build();
      nm.createNotificationChannel(ch);
    }
  }

  public static void setTooltip(ImageView v) {
    TooltipCompat.setTooltipText(v, v.getContentDescription());
  }


  public static void setNightTheme() {
    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);  //first reset night mode. Otherwise problems on some devices
    if (!SETTINGS.getForceDarkMode()) {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    } else {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    }
  }

  public static AlertDialog setDialogBg(AlertDialog dialog) {
    Window window = dialog.getWindow();
    if (window != null) {
      window.setBackgroundDrawableResource(R.drawable.alert_dialog_bg_bordered);
      window.setWindowAnimations(android.R.style.Animation_Dialog);
    }
    return dialog;
  }

  public static int dpToPx(float dp) {
    return (int)
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, Resources.getSystem().getDisplayMetrics());
  }

  //////////////////////////////////////////////////////////////////
  ///////////////////////////// LOGGING ////////////////////////////
  //////////////////////////////////////////////////////////////////

  private static final Object CRASH_LOG_LOCK = new Object();

  public static void writeCrashLog(String stackTrace) {
    synchronized (CRASH_LOG_LOCK) {
      File logFile = new File(App.getCxt().getExternalFilesDir(null), "gpscockpit_crash.log");
      boolean append = true;
      if (!logFile.exists()
          || logFile.length() > 512 * 1024
          || logFile.lastModified() < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90)) {
        append = false;
      }
      try {
        PrintWriter writer = new PrintWriter(new FileWriter(logFile, append));
        writer.println("=================================");
        writer.println(getDeviceInfo());
        writer.println("Time: " + getCurrDateTime(true));
        writer.println("Log ID: " + UUID.randomUUID().toString());
        writer.println("=================================");
        writer.println(stackTrace);
        writer.close();
      } catch (IOException ignored) {
      }
    }
  }

  public static int getPiFlags() {
    return PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE;
  }

  /**
   *
   * @param type true:latitude, false:longitude
   */
  public static String getDMSfromDD(Context context, Double coord, Boolean type){
    BigDecimal coordBD = new BigDecimal(coord);
    BigDecimal degrees = coordBD.setScale(0, RoundingMode.DOWN);
    BigDecimal minTemp = coordBD.subtract(degrees).multiply(new BigDecimal(60)).abs();
    BigDecimal minutes = minTemp.setScale(0,RoundingMode.DOWN);
    BigDecimal seconds = minTemp.subtract(minutes).multiply(new BigDecimal(60)).setScale(1,RoundingMode.HALF_UP);
    String hemisphere;
    if (type) {
      hemisphere = coord < 0 ? context.getString(com.redinput.compassview.R.string.compass_south) : context.getString(com.redinput.compassview.R.string.compass_north);
    } else {
      hemisphere = coord < 0 ? context.getString(com.redinput.compassview.R.string.compass_west) : context.getString(com.redinput.compassview.R.string.compass_east);
    }
    return degrees.abs()+"°"+minutes+"'"+seconds+"\"\u2009"+hemisphere;
  }
}
