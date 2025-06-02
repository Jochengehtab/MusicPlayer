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
 * Now with an OnItemClickListener so that tapping a row can be handled by the caller.
 */
public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.TrackViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Track track);
    }

    private final ArrayList<Track> tracks;
    private final OnItemClickListener listener;

    /**
     * @param tracks   the list of Track objects to display
     * @param listener callback to invoke when a row is tapped
     */
    public TrackAdapter(ArrayList<Track> tracks, OnItemClickListener listener) {
        this.tracks = tracks;
        this.listener = listener;
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

        // When this row is tapped, notify the listener, passing the Track object
        holder.itemView.setOnClickListener(v -> listener.onItemClick(current));
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    /** Simple ViewHolder that holds a reference to the title TextView. */
    static class TrackViewHolder extends RecyclerView.ViewHolder {
        final TextView titleText;

        TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.text_track_title);
        }
    }
}
