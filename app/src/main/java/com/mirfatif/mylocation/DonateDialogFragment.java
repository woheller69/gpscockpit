package com.mirfatif.mylocation;

import static com.mirfatif.mylocation.Utils.openWebUrl;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AlertDialog.Builder;
import androidx.appcompat.app.AppCompatDialogFragment;
import com.mirfatif.mylocation.databinding.DonateDialogBinding;

public class DonateDialogFragment extends AppCompatDialogFragment {

  public DonateDialogFragment() {}

  private MainActivity mA;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    mA = (MainActivity) getActivity();
  }

  private DonateDialogBinding mB;

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    mB = DonateDialogBinding.inflate(mA.getLayoutInflater());

    setButtonClickListener(mB.bitcoinButton, mB.bitcoinContainer);
    setButtonClickListener(mB.bankAccountButton, mB.bankAccountLink);
    setButtonClickListener(mB.playStoreButton, mB.playStoreLink);

    mB.bitcoinLink.setOnClickListener(v -> handleBitcoinClick());
    mB.playStoreLink.setOnClickListener(v -> openWebUrl(mA, getString(R.string.play_store_url)));
    String msg = Utils.getString(R.string.bank_account_request);
    mB.bankAccountLink.setOnClickListener(v -> Utils.sendMail(mA, msg));

    AlertDialog dialog =
        new Builder(mA).setTitle(R.string.donate_menu_item).setView(mB.getRoot()).create();
    return Utils.setDialogBg(dialog);
  }

  private void setButtonClickListener(View button, View detailsView) {
    button.setOnClickListener(
        v -> {
          hideAll();
          detailsView.setVisibility(View.VISIBLE);
        });
  }

  private void hideAll() {
    mB.bitcoinContainer.setVisibility(View.GONE);
    mB.bankAccountLink.setVisibility(View.GONE);
    mB.playStoreLink.setVisibility(View.GONE);
  }

  private void handleBitcoinClick() {
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setData(Uri.parse("bitcoin:" + Utils.getString(R.string.bitcoin_address)));
    if (App.getCxt()
        .getPackageManager()
        .queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .isEmpty()) {
      Utils.showToast(R.string.no_bitcoin_app_installed);
    } else {
      mA.startActivity(intent);
    }
  }
}
