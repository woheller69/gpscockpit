package com.mirfatif.mylocation;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;
import com.mirfatif.mylocation.MainActivity.Sat;
import com.mirfatif.mylocation.SatAdapter.SatViewHolder;
import com.mirfatif.mylocation.databinding.SatItemBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SatAdapter extends RecyclerView.Adapter<SatViewHolder> {

  private final List<Sat> mSats = new ArrayList<>();

  void submitList(List<Sat> sats) {
    synchronized (mSats) {
      mSats.clear();
      mSats.addAll(sats);
      notifyDataSetChanged();
    }
  }

  @Override
  public int getItemCount() {
    return mSats.size();
  }

  @NonNull
  @Override
  public SatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    return new SatViewHolder(SatItemBinding.inflate(inflater, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull SatViewHolder holder, int position) {
    holder.bind(position);
  }

  protected class SatViewHolder extends RecyclerView.ViewHolder {

    private final SatItemBinding mB;

    public SatViewHolder(@NonNull SatItemBinding binding) {
      super(binding.getRoot());
      mB = binding;
    }

    private void bind(int pos) {
      Sat sat;
      if (pos >= mSats.size() || (sat = mSats.get(pos)) == null) {
        return;
      }
      mB.idV.setText(String.valueOf(sat.mPrn));

      int strength = Math.min((int) (100 * sat.mSnr / Sat.maxSnr), 100);
      if (strength < 5) {
        strength = ThreadLocalRandom.current().nextInt(0, 3);
      }
      mB.progV.setProgress(strength);
      float ratio = Math.max(Math.min((float) strength / 100, 1), 0);
      int color = ColorUtils.blendARGB(Color.RED, Color.GREEN, ratio);
      mB.progV.setProgressTintList(ColorStateList.valueOf(color));

      mB.signalV.setText(String.valueOf(sat.mSnr));
      if (sat.mUsed) {
        mB.fixedV.setVisibility(View.VISIBLE);
      } else {
        mB.fixedV.setVisibility(View.GONE);
      }
    }
  }
}
