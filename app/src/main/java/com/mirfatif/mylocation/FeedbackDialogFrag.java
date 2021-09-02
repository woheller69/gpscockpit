package com.mirfatif.mylocation;

import static com.mirfatif.mylocation.MySettings.SETTINGS;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.mirfatif.mylocation.databinding.FeedbackDialogBinding;

public class FeedbackDialogFrag extends BottomSheetDialogFragment {

  private MainActivity mA;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    mA = (MainActivity) getActivity();
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
    dialog.setDismissWithAnimation(true);
    return dialog;
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {

    boolean isYes = requireArguments().getBoolean(YES);
    FeedbackDialogBinding b =
        FeedbackDialogBinding.inflate(getLayoutInflater(), container, container != null);

    b.msgV.setText(
        isYes
            ? (BuildConfig.IS_PS ? R.string.rate_the_app : R.string.purchase_and_rate_the_app)
            : R.string.ask_to_provide_feedback);
    b.neutralButton.setText(R.string.do_not_ask);
    b.posButton.setText(
        isYes ? (BuildConfig.IS_PS ? R.string.rate : R.string.i_will) : R.string.contact);

    b.neutralButton.setOnClickListener(
        v -> {
          SETTINGS.setAskForFeedbackTs(Long.MAX_VALUE);
          dismiss();
        });

    b.posButton.setOnClickListener(
        v -> {
          dismiss();
          if (isYes) {
            if (BuildConfig.IS_PS) {
              Utils.openWebUrl(mA, Utils.getString(R.string.play_store_url));
            } else {
              DonateDialogFragment.show(mA);
            }
          } else {
            AboutDialogFragment.show(mA);
          }
          Utils.showToast(R.string.thank_you);
        });

    b.negButton.setOnClickListener(v -> dismiss());

    return b.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    /*
     Replace the default white background with the custom on which has
     round corners and background color set.
     Another option is to override the bottomSheetDialog theme in style.xml
    */
    ((View) view.getParent()).setBackgroundResource(R.drawable.bottom_sheet_background);
  }

  private static final String YES = "IS_YES";

  public static void show(FragmentActivity activity, boolean isYes) {
    FeedbackDialogFrag frag = new FeedbackDialogFrag();
    Bundle args = new Bundle();
    args.putBoolean(YES, isYes);
    frag.setArguments(args);
    frag.show(activity.getSupportFragmentManager(), "FEEDBACK_RATING");
  }
}
