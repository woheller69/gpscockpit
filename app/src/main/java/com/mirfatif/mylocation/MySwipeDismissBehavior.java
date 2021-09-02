package com.mirfatif.mylocation;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.customview.widget.ViewDragHelper;

// com.google.android.material.behavior.SwipeDismissBehavior
public class MySwipeDismissBehavior extends CoordinatorLayout.Behavior<View> {

  private static final float mSensitivity = 0f;
  private static final float mDragDismissThreshold = 1f;

  private ViewDragHelper mViewDragHelper;
  private final OnDismissListener mListener;
  private boolean mInterceptingEvents;

  public interface OnDismissListener {
    void onDismiss();
  }

  public MySwipeDismissBehavior(OnDismissListener listener) {
    mListener = listener;
  }

  @Override
  public boolean onLayoutChild(
      @NonNull CoordinatorLayout parent, @NonNull View child, int layoutDirection) {
    boolean handled = super.onLayoutChild(parent, child, layoutDirection);
    if (ViewCompat.getImportantForAccessibility(child)
        == ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
      ViewCompat.setImportantForAccessibility(child, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
      updateAccessibilityActions(child);
    }
    return handled;
  }

  @Override
  public boolean onInterceptTouchEvent(
      @NonNull CoordinatorLayout parent, @NonNull View child, @NonNull MotionEvent event) {
    boolean dispatchEventToHelper = mInterceptingEvents;

    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        mInterceptingEvents =
            parent.isPointInChildBounds(child, (int) event.getX(), (int) event.getY());
        dispatchEventToHelper = mInterceptingEvents;
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        // Reset the ignore flag for next times
        mInterceptingEvents = false;
        break;
    }

    if (dispatchEventToHelper) {
      ensureViewDragHelper(parent);
      return mViewDragHelper.shouldInterceptTouchEvent(event);
    }
    return false;
  }

  @Override
  public boolean onTouchEvent(
      @NonNull CoordinatorLayout parent, @NonNull View child, @NonNull MotionEvent event) {
    if (mViewDragHelper != null) {
      mViewDragHelper.processTouchEvent(event);
      return true;
    }
    return false;
  }

  private final ViewDragHelper.Callback dragCallback =
      new ViewDragHelper.Callback() {
        private static final int INVALID_POINTER_ID = -1;

        private int originalCapturedViewLeft;
        private int activePointerId = INVALID_POINTER_ID;

        @Override
        public boolean tryCaptureView(@NonNull View child, int pointerId) {
          // Only capture if we don't already have an active pointer id
          return (activePointerId == INVALID_POINTER_ID || activePointerId == pointerId);
        }

        @Override
        public void onViewCaptured(@NonNull View capturedChild, int activePointerId) {
          this.activePointerId = activePointerId;
          originalCapturedViewLeft = capturedChild.getLeft();

          /*
           The view has been captured, and thus a drag is about to
           start so stop any parents intercepting
          */
          final ViewParent parent = capturedChild.getParent();
          if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
          }
        }

        @Override
        public void onViewDragStateChanged(int state) {}

        @Override
        public void onViewReleased(@NonNull View child, float xvel, float yvel) {
          // Reset the active pointer ID
          activePointerId = INVALID_POINTER_ID;

          final int childWidth = child.getWidth();
          int targetLeft;
          boolean dismiss = false;

          if (shouldDismiss(child, xvel)) {
            targetLeft =
                child.getLeft() < originalCapturedViewLeft
                    ? originalCapturedViewLeft - childWidth
                    : originalCapturedViewLeft + childWidth;
            dismiss = true;
          } else {
            // Else, reset back to the original left
            targetLeft = originalCapturedViewLeft;
          }

          if (mViewDragHelper.settleCapturedViewAt(targetLeft, child.getTop())) {
            ViewCompat.postOnAnimation(child, new SettleRunnable(child, dismiss));
          } else if (dismiss && mListener != null) {
            mListener.onDismiss();
          }
        }

        private boolean shouldDismiss(@NonNull View child, float xvel) {
          if (xvel != 0f) {
            return true;
          } else {
            final int distance = child.getLeft() - originalCapturedViewLeft;
            final int thresholdDistance = Math.round(child.getWidth() * mDragDismissThreshold);
            return Math.abs(distance) >= thresholdDistance;
          }
        }

        @Override
        public int getViewHorizontalDragRange(@NonNull View child) {
          return child.getWidth();
        }

        @Override
        public int clampViewPositionHorizontal(@NonNull View child, int left, int dx) {
          int min = originalCapturedViewLeft - child.getWidth();
          int max = originalCapturedViewLeft + child.getWidth();
          return Math.min(Math.max(min, left), max);
        }

        @Override
        public int clampViewPositionVertical(@NonNull View child, int top, int dy) {
          return child.getTop();
        }
      };

  private void ensureViewDragHelper(ViewGroup parent) {
    if (mViewDragHelper == null) {
      mViewDragHelper = ViewDragHelper.create(parent, mSensitivity, dragCallback);
    }
  }

  private class SettleRunnable implements Runnable {
    private final View view;
    private final boolean dismiss;

    SettleRunnable(View view, boolean dismiss) {
      this.view = view;
      this.dismiss = dismiss;
    }

    @Override
    public void run() {
      if (mViewDragHelper != null && mViewDragHelper.continueSettling(true)) {
        ViewCompat.postOnAnimation(view, this);
      } else {
        if (dismiss && mListener != null) {
          mListener.onDismiss();
        }
      }
    }
  }

  private void updateAccessibilityActions(View child) {
    ViewCompat.removeAccessibilityAction(child, AccessibilityNodeInfoCompat.ACTION_DISMISS);
    ViewCompat.replaceAccessibilityAction(
        child,
        AccessibilityActionCompat.ACTION_DISMISS,
        null,
        (view, arguments) -> {
          int offset = view.getWidth();
          ViewCompat.offsetLeftAndRight(view, offset);
          view.setAlpha(0f);
          if (mListener != null) {
            mListener.onDismiss();
          }
          return true;
        });
  }
}
