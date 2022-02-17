package org.woheller69.gpscockpit;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AlertDialog.Builder;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.FragmentActivity;
import org.woheller69.gpscockpit.databinding.AboutDialogBinding;

public class AboutDialogFragment extends AppCompatDialogFragment {

  private AboutDialogFragment() {}

  private MainActivity mA;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    mA = (MainActivity) getActivity();
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    AboutDialogBinding b = AboutDialogBinding.inflate(mA.getLayoutInflater());
    b.version.setText(BuildConfig.VERSION_NAME);
    b.sourceCode.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        mA.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getResources().getString(R.string.source_url))));
      }
    });

    AlertDialog dialog = new Builder(mA).setView(b.getRoot()).create();
    return Utils.setDialogBg(dialog);
  }

  public static void show(FragmentActivity activity) {
    new AboutDialogFragment().show(activity.getSupportFragmentManager(), "ABOUT");
  }
}
