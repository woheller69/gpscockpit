package com.mirfatif.mylocation;

import static com.mirfatif.mylocation.MySettings.SETTINGS;

import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams;
import com.mirfatif.mylocation.MySwipeDismissBehavior.OnDismissListener;
import com.mirfatif.mylocation.databinding.ActivityMainBinding;

class Feedback {

  private final MainActivity mA;
  private final ActivityMainBinding mB;

  Feedback(MainActivity activity) {
    mA = activity;
    mB = mA.getRootView();
  }

  void askForFeedback() {
    if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
      Utils.runUi(mA, this::askForFeedback);
      return;
    }
    if (SETTINGS.shouldAskForFeedback()) {
      mB.feedbackCont.setVisibility(View.VISIBLE);
    }

    if (mB.feedbackCont.getVisibility() != View.VISIBLE) {
      return;
    }

    OnDismissListener listener = () -> mB.feedbackCont.setVisibility(View.GONE);
    MySwipeDismissBehavior dismissBehavior = new MySwipeDismissBehavior(listener);
    ((LayoutParams) mB.feedbackCont.getLayoutParams()).setBehavior(dismissBehavior);

    Animation animation = AnimationUtils.loadAnimation(App.getCxt(), R.anim.shake);
    Runnable shake = () -> mB.feedbackCont.startAnimation(animation);
    mB.feedbackCont.postDelayed(shake, 1000);
  }
}
