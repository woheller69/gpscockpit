package com.mirfatif.mylocation;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LicenseVerifierFlav {

  private final Runnable mTask;

  LicenseVerifierFlav(MainActivity activity) {
    mTask = () -> new Feedback(activity).askForFeedback();
  }

  private final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();

  void check() {
    EXECUTOR.schedule(mTask, 2, TimeUnit.SECONDS);
  }

  void onDestroy() {}

  public boolean isVerified() {
    return true;
  }
}
