package org.woheller69.gpscockpit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;
import org.woheller69.gpscockpit.MainActivity.Sat;
import org.woheller69.gpscockpit.SatAdapter.SatViewHolder;
import org.woheller69.gpscockpit.databinding.SatItemBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SatAdapter extends RecyclerView.Adapter<SatViewHolder> {

  private final List<Sat> mSats = new ArrayList<>();

  @SuppressLint("NotifyDataSetChanged")
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
    return new SatViewHolder(SatItemBinding.inflate(inflater, parent, false),parent.getContext());
  }

  @Override
  public void onBindViewHolder(@NonNull SatViewHolder holder, int position) {
    holder.bind(position);
  }

  protected class SatViewHolder extends RecyclerView.ViewHolder {

    private final SatItemBinding mB;
    private Context mcontext;

    public SatViewHolder(@NonNull SatItemBinding binding, Context context) {
      super(binding.getRoot());
      mB = binding;
      mcontext = context;
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
      int color = ColorUtils.blendARGB(Utils.getThemeColor(mcontext,R.attr.colorSurface), Utils.getThemeColor(mcontext,R.attr.colorPrimary), ratio);
      mB.progV.setProgressTintList(ColorStateList.valueOf(color));

      mB.signalV.setText(String.valueOf(sat.mSnr));
      if (sat.mUsed) {
        mB.fixedV.setVisibility(View.VISIBLE);
      } else {
        mB.fixedV.setVisibility(View.INVISIBLE);
      }
    }
  }
}
