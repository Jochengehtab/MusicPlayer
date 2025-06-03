package com.jochengehtab.musicplayer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView.Adapter that binds a list of Track records into item_track.xml rows,
 * shows a three‐dot overflow menu, and uses DiffUtil to dispatch updates.
 */
public class TrackAdapter extends RecyclerView.Adapter<TrackViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Track track);
    }

    private final Context context;
    private final OnItemClickListener listener;

    /** Backing list of Tracks; do not modify externally. */
    private final List<Track> tracks = new ArrayList<>();

    public TrackAdapter(
            @NonNull Context context,
            @NonNull List<Track> initialTracks,
            @NonNull OnItemClickListener listener
    ) {
        this.context = context;
        this.listener = listener;
        this.tracks.addAll(initialTracks);
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_track, parent, false);
        return new TrackViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        Track current = tracks.get(position);
        holder.titleText.setText(current.title());

        // 1) Row click = play the track via listener
        holder.itemView.setOnClickListener(v -> listener.onItemClick(current));

        // 2) Overflow icon click = show PopupMenu
        holder.overflowIcon.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, holder.overflowIcon);
            popup.inflate(R.menu.track_item_menu);

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_add_favorite) {
                    Toast.makeText(context,
                            "Added \"" + current.title() + "\" to Favorites",
                            Toast.LENGTH_SHORT).show();
                    return true;
                } else if (id == R.id.action_delete) {
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

    /**
     * Replace the adapter’s contents with newList, using DiffUtil
     * to compute minimal insert/remove/change operations.
     */
    public void updateList(@NonNull List<Track> newList) {
        TrackDiffCallback diffCallback = new TrackDiffCallback(this.tracks, newList);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

        this.tracks.clear();
        this.tracks.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }
}
