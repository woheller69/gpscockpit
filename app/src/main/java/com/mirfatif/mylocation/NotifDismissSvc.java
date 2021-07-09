package com.mirfatif.mylocation;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class NotifDismissSvc extends Service {

  public static final String EXTRA_INTENT_TYPE = BuildConfig.APPLICATION_ID + ".extra.INTENT_TYPE";
  public static final String EXTRA_NOTIF_ID = BuildConfig.APPLICATION_ID + ".extra.NOTIF_ID";
  public static final int INTENT_TYPE_ACTIVITY = 1;
  public static final int INTENT_TYPE_SERVICE = 2;
  private static final int NONE = -1;

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    int type = intent.getIntExtra(EXTRA_INTENT_TYPE, NONE);
    int id = intent.getIntExtra(EXTRA_NOTIF_ID, NONE);
    if (type != NONE && id != NONE) {
      NotificationManager.from(App.getCxt()).cancel(id);
      intent.setComponent(null);
      intent.removeExtra(EXTRA_INTENT_TYPE);
      intent.removeExtra(EXTRA_NOTIF_ID);
      if (type == INTENT_TYPE_ACTIVITY) {
        // FLAG_ACTIVITY_NEW_TASK is required to start Activity from outside an Activity
        intent.setFlags(intent.getFlags() | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
      } else if (type == INTENT_TYPE_SERVICE) {
        startService(intent);
      }
    }
    stopSelf(startId); // Stop if no pending requests
    return Service.START_NOT_STICKY;
  }
}
