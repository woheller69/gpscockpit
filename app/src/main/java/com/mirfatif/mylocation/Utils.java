package com.mirfatif.mylocation;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
import static android.text.style.DynamicDrawableSpan.ALIGN_BASELINE;
import static com.mirfatif.mylocation.MySettings.SETTINGS;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.SystemClock;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BulletSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Window;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.TooltipCompat;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsService;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.text.HtmlCompat;
import androidx.lifecycle.Lifecycle.State;
import androidx.lifecycle.LifecycleOwner;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme;
import androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme;
import androidx.security.crypto.MasterKey;
import androidx.security.crypto.MasterKey.KeyScheme;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.RoundingMode;
import java.security.GeneralSecurityException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  public static boolean openWebUrl(Activity activity, String url) {
    Intent intent = new Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION);
    PackageManager pm = App.getCxt().getPackageManager();
    int flags = VERSION.SDK_INT >= VERSION_CODES.M ? PackageManager.MATCH_ALL : 0;
    List<ResolveInfo> infoList = pm.queryIntentServices(intent, flags);
    boolean customTabsSupported = !infoList.isEmpty();

    if (customTabsSupported) {
      CustomTabColorSchemeParams colorSchemeParams =
          new CustomTabColorSchemeParams.Builder()
              .setToolbarColor(ContextCompat.getColor(App.getCxt(),R.color.primary))
              .build();
      CustomTabsIntent customTabsIntent =
          new CustomTabsIntent.Builder()
              .setShareState(CustomTabsIntent.SHARE_STATE_ON)
              .setDefaultColorSchemeParams(colorSchemeParams)
              .build();
      customTabsIntent.launchUrl(activity, Uri.parse(url));
      return true;
    }

    intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
    intent.addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    try {
      activity.startActivity(intent);
      return true;
    } catch (ActivityNotFoundException ignored) {
    }

    if (VERSION.SDK_INT >= VERSION_CODES.R) {
      intent.setFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER);
      try {
        activity.startActivity(intent);
        return true;
      } catch (ActivityNotFoundException ignored) {
      }
    }

    showToast(R.string.no_browser_installed);
    return true;
  }

  public static void sendMail(Activity activity, String body) {
    Intent emailIntent = new Intent(Intent.ACTION_SENDTO).setData(Uri.parse("mailto:"));
    emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[] {getString(R.string.email_address)});
    emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
    if (body != null) {
      emailIntent.putExtra(Intent.EXTRA_TEXT, body);
    }
    try {
      activity.startActivity(emailIntent);
    } catch (ActivityNotFoundException e) {
      showToast(R.string.no_email_app_installed);
    }
  }

  //////////////////////////////////////////////////////////////////
  /////////////////////////// FORMATTING ///////////////////////////
  //////////////////////////////////////////////////////////////////

  private static final DecimalFormat sLatLngFormat = new DecimalFormat();
  private static final DecimalFormat intFormat = new DecimalFormat("0");

  static {
    sLatLngFormat.setMaximumFractionDigits(5);
  }

  public static String formatInt(double value) {
    intFormat.setRoundingMode(RoundingMode.HALF_UP);
    return intFormat.format(value).replaceAll("^-(?=0(\\.0*)?$)", "");  //remove the minus sign if it's followed by 0-n characters of "0.00000..."
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

  public static Spanned htmlToString(int resId) {
    return htmlToString(getString(resId));
  }

  private static final String IMG_SPAN_LINK = getString(R.string.img_span_link);

  public static Spanned htmlToString(String str) {
    Spanned spanned = HtmlCompat.fromHtml(str, HtmlCompat.FROM_HTML_MODE_COMPACT);

    // Let's customize BulletSpans
    SpannableStringBuilder string = new SpannableStringBuilder(spanned);

    Parcel parcel = Parcel.obtain();
    parcel.writeInt(dpToPx(4)); // gapWidth
    parcel.writeInt(0); // wantColor
    parcel.writeInt(0); // color
    parcel.writeInt(dpToPx(2)); // bulletRadius

    for (BulletSpan span : string.getSpans(0, string.length(), BulletSpan.class)) {
      int start = string.getSpanStart(span);
      int end = string.getSpanEnd(span);
      string.removeSpan(span);
      parcel.setDataPosition(0); // For read
      string.setSpan(new BulletSpan(parcel), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    parcel.recycle();

    Drawable d = ResourcesCompat.getDrawable(App.getRes(), R.drawable.link, null);
    if (d != null) {
      // DrawableCompat.setTint()
      d.setTint(ContextCompat.getColor(App.getCxt(),R.color.accent));
      d.setBounds(0, 0, dpToPx(12), dpToPx(12));
    }

    for (URLSpan span : string.getSpans(0, string.length(), URLSpan.class)) {
      int start = string.getSpanStart(span);
      int end = string.getSpanEnd(span);
      if (!string.toString().substring(start, end).equals(IMG_SPAN_LINK)) {
        continue;
      }
      string.setSpan(new ImageSpan(d, ALIGN_BASELINE), start, end, SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    breakParas(string);
    return string;
  }

  @SuppressWarnings("UnusedReturnValue")
  public static SpannableStringBuilder breakParas(SpannableStringBuilder string) {
    // Remove newLine chars at end
    while (true) {
      int len = string.length();
      if (string.charAt(len - 1) != '\n') {
        break;
      }
      string.delete(len - 1, len);
    }

    Matcher matcher = Pattern.compile("\n").matcher(string);
    int from = 0;
    while (matcher.find(from)) {
      // Replace the existing newLine char with 2 newLine chars
      string.replace(matcher.start(), matcher.end(), "\n\n");
      // On next iteration skip the newly added newLine char
      from = matcher.end() + 1;

      // Add span to the newly added newLine char
      string.setSpan(
          new RelativeSizeSpan(0.25f),
          matcher.start() + 1,
          matcher.end() + 1,
          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

      // On Android 7 Matcher is not refreshed if the string is changed
      matcher = Pattern.compile("\n").matcher(string);
    }

    return string;
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

  public static SharedPreferences getDefPrefs() {
    return App.getCxt().getSharedPreferences("def_prefs", Context.MODE_PRIVATE);
  }

  private static SharedPreferences sEncPrefs;
  private static final Object ENC_PREFS_LOCK = new Object();

  @SuppressWarnings("UnusedReturnValue")
  public static SharedPreferences getEncPrefs() {
    synchronized (ENC_PREFS_LOCK) {
      if (sEncPrefs != null) {
        return sEncPrefs;
      }

      for (int i = 0; i < 10; i++) {
        try {
          sEncPrefs =
              EncryptedSharedPreferences.create(
                  App.getCxt(),
                  BuildConfig.APPLICATION_ID + "_nb_prefs",
                  new MasterKey.Builder(App.getCxt()).setKeyScheme(KeyScheme.AES256_GCM).build(),
                  PrefKeyEncryptionScheme.AES256_SIV,
                  PrefValueEncryptionScheme.AES256_GCM);
          return sEncPrefs;
        } catch (Exception e) {
          if (i == 9) {
            e.printStackTrace();
          } else {
            Log.e(TAG, "getEncPrefs: " + e.toString());
          }
          SystemClock.sleep(100);
        }
      }

      // Temp fix for https://github.com/google/tink/issues/413
      return sEncPrefs = getEncPrefsInternal();
    }
  }

  @SuppressLint("HardwareIds")
  @SuppressWarnings("deprecation")
  private static SharedPreferences getEncPrefsInternal() {
    try {
      return EncryptedSharedPreferences.create(
          BuildConfig.APPLICATION_ID + "_nb_prefs2",
          Secure.getString(App.getCxt().getContentResolver(), Secure.ANDROID_ID),
          App.getCxt(),
          PrefKeyEncryptionScheme.AES256_SIV,
          PrefValueEncryptionScheme.AES256_GCM);
    } catch (GeneralSecurityException | IOException e) {
      e.printStackTrace();
      throw new Error(e);
    }
  }

  public static Context setLocale(Context context) {
    String lang = SETTINGS.getLocale();
    Locale locale;
    if (TextUtils.isEmpty(lang)) {
      if (VERSION.SDK_INT >= VERSION_CODES.N) {
        locale = Resources.getSystem().getConfiguration().getLocales().get(0);
      } else {
        locale = Resources.getSystem().getConfiguration().locale;
      }
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

  public static boolean isNightMode(Activity activity) {
    int uiMode = activity.getResources().getConfiguration().uiMode;
    return (uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
  }

  public static boolean setNightTheme(Activity activity) {
    if (!SETTINGS.getForceDarkMode()) {
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
      return false;
    }

    // Dark Mode applied on whole device
    if (isNightMode(activity)) {
      return false;
    }

    // Dark Mode already applied in app
    int defMode = AppCompatDelegate.getDefaultNightMode();
    if (defMode == AppCompatDelegate.MODE_NIGHT_YES) {
      return false;
    }

    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    return true;
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
      File logFile = new File(App.getCxt().getExternalFilesDir(null), "MyLocation_crash.log");
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
        showCrashNotification(logFile);
      } catch (IOException ignored) {
      }
    }
  }

  private static void showCrashNotification(File logFile) {
    if (!SETTINGS.shouldAskToSendCrashReport()) {
      return;
    }

    String authority = BuildConfig.APPLICATION_ID + ".FileProvider";
    Uri logFileUri = FileProvider.getUriForFile(App.getCxt(), authority, logFile);

    final String CHANNEL_ID = "channel_crash_report";
    final String CHANNEL_NAME = getString(R.string.channel_crash_report);
    final int UNIQUE_ID = getInteger(R.integer.channel_crash_report);

    Intent intent = new Intent(Intent.ACTION_SEND);
    intent
        .setData(logFileUri)
        .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_EMAIL, new String[] {getString(R.string.email_address)})
        .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " - Crash Report")
        .putExtra(Intent.EXTRA_TEXT, "Find attachment.")
        .putExtra(Intent.EXTRA_STREAM, logFileUri);

    // Adding extra information to dismiss notification after the action is tapped
    intent
        .setClass(App.getCxt(), NotifDismissSvc.class)
        .putExtra(NotifDismissSvc.EXTRA_INTENT_TYPE, NotifDismissSvc.INTENT_TYPE_ACTIVITY)
        .putExtra(NotifDismissSvc.EXTRA_NOTIF_ID, UNIQUE_ID);

    PendingIntent pi = getNotifDismissSvcPi(UNIQUE_ID, intent);
    createNotifChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManagerCompat.IMPORTANCE_HIGH);

    NotificationCompat.Builder nb =
        new NotificationCompat.Builder(App.getCxt(), CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(getString(R.string.crash_report))
            .setContentText(getString(R.string.ask_to_report_crash_small))
            .setStyle(
                new NotificationCompat.BigTextStyle()
                    .bigText(getString(R.string.ask_to_report_crash)))
            .setContentIntent(pi)
            .addAction(0, getString(R.string.send_report), pi)
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true);

    NotificationManagerCompat.from(App.getCxt()).notify(UNIQUE_ID, nb.build());
  }

  private static PendingIntent getNotifDismissSvcPi(int uniqueId, Intent intent) {
    return PendingIntent.getService(App.getCxt(), uniqueId, intent, getPiFlags());
  }

  public static int getPiFlags() {
    return PendingIntent.FLAG_UPDATE_CURRENT;
  }
}
