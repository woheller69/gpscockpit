package com.mirfatif.mylocation;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AlertDialog.Builder;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.mirfatif.mylocation.MainActivity.Sat;
import com.mirfatif.mylocation.databinding.RvSatsBinding;
import java.util.List;

public class SatsDialogFragment extends AppCompatDialogFragment {

  public SatsDialogFragment() {}

  private MainActivity mA;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    mA = (MainActivity) getActivity();
  }

  private SatAdapter mAdapter;

  synchronized void submitList(List<Sat> satList) {
    mAdapter.submitList(satList);
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    RvSatsBinding b = RvSatsBinding.inflate(mA.getLayoutInflater());

    mAdapter = new SatAdapter();
    b.rv.setAdapter(mAdapter);
    LinearLayoutManager layoutManager = new LinearLayoutManager(mA);
    b.rv.setLayoutManager(layoutManager);
    b.rv.addItemDecoration(new DividerItemDecoration(mA, DividerItemDecoration.VERTICAL));

    AlertDialog d = new Builder(mA).setTitle(R.string.satellites).setView(b.getRoot()).create();
    return Utils.setDialogBg(d);
  }

  // We cannot use Dialog's OnDismiss and OnCancel Listeners, DialogFragment owns them.
  private OnDismissListener mDismissListener;

  public void setOnDismissListener(OnDismissListener dismissListener) {
    mDismissListener = dismissListener;
  }

  @Override
  public void onDismiss(@NonNull DialogInterface dialog) {
    super.onDismiss(dialog);
    if (mDismissListener != null) {
      mDismissListener.onDismiss(dialog);
    }
  }
}
