package com.jochengehtab.musicplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/**
 * RecyclerView.Adapter that binds a list of Track objects into item_track.xml rows.
 */
public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.TrackViewHolder> {

    private final ArrayList<Track> tracks;

    public TrackAdapter(ArrayList<Track> tracks) {
        this.tracks = tracks;
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate item_track.xml
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_track, parent, false);
        return new TrackViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        Track current = tracks.get(position);
        holder.titleText.setText(current.getTitle());
        // If you want click‐handling per‐item, you could do it here:
        // holder.itemView.setOnClickListener(v -> { … });
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    /**
     * Simple ViewHolder that holds a reference to the title TextView.
     */
    static class TrackViewHolder extends RecyclerView.ViewHolder {
        final TextView titleText;

        TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.text_track_title);
        }
    }
}
