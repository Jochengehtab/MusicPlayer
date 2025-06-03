package com.jochengehtab.musicplayer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/**
 * RecyclerView.Adapter that binds a list of Track objects into item_track.xml rows,
 * showing a three‐dot overflow menu for each item.
 */
public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.TrackViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Track track);
    }

    private final ArrayList<Track> tracks;
    private final OnItemClickListener listener;
    private final Context context;

    public TrackAdapter(Context context, ArrayList<Track> tracks, OnItemClickListener listener) {
        this.context = context;
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
        holder.titleText.setText(current.title());

        // 1) Row click = play the track
        holder.itemView.setOnClickListener(v -> listener.onItemClick(current));

        // 2) Overflow icon click = show PopupMenu
        holder.overflowIcon.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, holder.overflowIcon);
            popup.inflate(R.menu.track_item_menu);
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_add_favorite) {
                    // Example: show a toast; replace with real logic
                    Toast.makeText(context,
                            "Added \"" + current.title() + "\" to Favorites",
                            Toast.LENGTH_SHORT).show();
                    return true;
                } else if (id == R.id.action_delete) {
                    // Example: show a toast; replace with real deletion logic
                    Toast.makeText(context,
                            "Deleted \"" + current.title() + "\"",
                            Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    static class TrackViewHolder extends RecyclerView.ViewHolder {
        final TextView titleText;
        final ImageView overflowIcon;

        TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.text_track_title);
            overflowIcon = itemView.findViewById(R.id.overflow_icon);
        }
    }
}
