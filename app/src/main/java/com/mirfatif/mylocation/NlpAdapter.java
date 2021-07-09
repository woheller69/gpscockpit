package com.mirfatif.mylocation;

import static com.mirfatif.mylocation.MySettings.SETTINGS;
import static com.mirfatif.mylocation.Utils.getString;
import static com.mirfatif.mylocation.Utils.hasCoarseLocPerm;
import static com.mirfatif.mylocation.Utils.isNaN;
import static com.mirfatif.mylocation.Utils.setTooltip;

import android.location.Location;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.mirfatif.mylocation.NlpAdapter.NlpViewHolder;
import com.mirfatif.mylocation.databinding.NlpItemBinding;
import java.util.List;

public class NlpAdapter extends RecyclerView.Adapter<NlpViewHolder> {

  private final List<NlpBackend> mBackends;
  private final NlpClickListener mListener;

  NlpAdapter(NlpClickListener listener, List<NlpBackend> backends) {
    mListener = listener;
    mBackends = backends;
  }

  @NonNull
  @Override
  public NlpViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    return new NlpViewHolder(NlpItemBinding.inflate(inflater, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull NlpViewHolder holder, int position) {
    holder.bind(position);
  }

  @Override
  public int getItemCount() {
    return mBackends.size();
  }

  protected class NlpViewHolder extends RecyclerView.ViewHolder {

    private final NlpItemBinding mB;

    public NlpViewHolder(NlpItemBinding binding) {
      super(binding.getRoot());
      mB = binding;
      setTooltip(mB.map);
      setTooltip(mB.copy);
      mB.map.setOnClickListener(
          v -> {
            int pos = getBindingAdapterPosition();
            NlpBackend backend;
            if (pos != RecyclerView.NO_POSITION
                && pos < mBackends.size()
                && (backend = mBackends.get(pos)) != null)
              mListener.mapClicked(backend.getLocation());
          });
      mB.copy.setOnClickListener(
          v -> {
            int pos = getBindingAdapterPosition();
            NlpBackend backend;
            if (pos != RecyclerView.NO_POSITION
                && pos < mBackends.size()
                && (backend = mBackends.get(pos)) != null)
              mListener.copyClicked(backend.getLocation());
          });
      mB.settings.setOnClickListener(
          v -> {
            int pos = getBindingAdapterPosition();
            NlpBackend backend;
            if (pos != RecyclerView.NO_POSITION
                && pos < mBackends.size()
                && (backend = mBackends.get(pos)) != null)
              mListener.settingsClicked(backend.getPkgName());
          });
    }

    private void bind(int pos) {
      NlpBackend backend;
      if (pos >= mBackends.size() || (backend = mBackends.get(pos)) == null) {
        return;
      }

      String state = null, lat = "--", lng = "--", acc = "--", time = "--";
      boolean locAvailable = false;
      if (SETTINGS.getNlpEnabled() && hasCoarseLocPerm()) {
        if (backend.permsRequired()) {
          state = getString(R.string.perm_not_granted);
        } else if (backend.failed()) {
          state = getString(R.string.failed);
        } else {
          Location loc = backend.getLocation();
          if (loc != null && !isNaN(loc.getLatitude()) && !isNaN(loc.getLongitude())) {
            locAvailable = true;
            lat = Utils.formatLatLng(loc.getLatitude());
            lng = Utils.formatLatLng(loc.getLongitude());
            if (!isNaN(loc.getAccuracy()) && loc.getAccuracy() != 0) {
              acc = getString(R.string.acc_unit, Utils.formatLocAccuracy(loc.getAccuracy()));
            }
            long curr = System.currentTimeMillis();
            long t = loc.getTime();
            t = t - Math.max(0, t - curr);
            time = DateUtils.getRelativeTimeSpanString(t).toString();
          }
        }
      }
      mB.name.setText(backend.getLabel());
      int vis = state == null ? View.VISIBLE : View.GONE;
      mB.map.setVisibility(vis);
      mB.copy.setVisibility(vis);
      mB.map.setEnabled(locAvailable);
      mB.copy.setEnabled(locAvailable);
      vis = state == null ? View.GONE : View.VISIBLE;
      mB.settings.setVisibility(vis);
      mB.stateV.setText(state);
      mB.latV.setText(lat);
      mB.lngV.setText(lng);
      mB.accV.setText(acc);
      mB.timeV.setText(time);
    }
  }

  interface NlpClickListener {

    void mapClicked(Location loc);

    void copyClicked(Location loc);

    void settingsClicked(String pkg);
  }
}
