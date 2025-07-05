package com.jochengehtab.musicplayer.MusicList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.jochengehtab.musicplayer.Music.MusicUtility;
import com.jochengehtab.musicplayer.MusicList.Options.Rename;
import com.jochengehtab.musicplayer.MusicList.Options.Reset;
import com.jochengehtab.musicplayer.MusicList.Options.Trim;
import com.jochengehtab.musicplayer.R;

import java.util.ArrayList;
import java.util.List;

public class TrackAdapter extends RecyclerView.Adapter<TrackViewHolder> {
    private final Context context;
    private final OnItemClickListener listener;
    private final List<Track> tracks = new ArrayList<>();
    private final Trim trim;
    private final Rename rename;
    private final Reset reset;

    public TrackAdapter(
            Context context,
            List<Track> initialTracks,
            OnItemClickListener listener,
            MusicUtility musicUtility
    ) {
        this.context = context;
        this.listener = listener;
        this.tracks.addAll(initialTracks);
        this.trim = new Trim(context, musicUtility);
        this.rename = new Rename(context);
        this.reset = new Reset();
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_track, parent, false);
        return new TrackViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(TrackViewHolder holder, int position) {
        Track current = tracks.get(position);
        holder.titleText.setText(current.title());

        // Handles clicks on a track in the list
        holder.itemView.setOnClickListener(v -> {
            listener.onItemClick(current);
        });

        holder.overflowIcon.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, holder.overflowIcon);
            popup.inflate(R.menu.track_item_menu);

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.edit) {
                    trim.showTrimDialog(current);
                    return true;
                } else if (id == R.id.action_rename) {
                    rename.showRenameDialog(
                            current,
                            position,
                            tracks,
                            this::updateList
                    );
                    return true;
                } else if (id == R.id.action_reset) {
                    reset.reset(current);
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

    public void updateList(List<Track> newList) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new TrackDiffCallback(this.tracks, newList)
        );
        this.tracks.clear();
        this.tracks.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }
}