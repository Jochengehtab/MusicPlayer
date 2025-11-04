package com.jochengehtab.musicplayer.MusicList;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.jochengehtab.musicplayer.MainActivity.MainActivity;
import com.jochengehtab.musicplayer.Music.MusicUtility;
import com.jochengehtab.musicplayer.MusicList.Options.Rename;
import com.jochengehtab.musicplayer.MusicList.Options.Reset;
import com.jochengehtab.musicplayer.MusicList.Options.Trim;
import com.jochengehtab.musicplayer.R;
import com.jochengehtab.musicplayer.data.AppDatabase;
import com.jochengehtab.musicplayer.data.Playlist;
import com.jochengehtab.musicplayer.data.PlaylistTrackCrossRef;
import com.jochengehtab.musicplayer.data.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrackAdapter extends RecyclerView.Adapter<TrackViewHolder> {
    private final Context context;
    private final OnItemClickListener listener;
    private final List<Track> tracks = new ArrayList<>();
    private final AppDatabase database;
    private String currentPlaylistName = MainActivity.ALL_TRACKS_PLAYLIST_NAME;

    // Executor for background database operations
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Options (assuming they are updated or still relevant)
    private final Trim trim;
    private final Rename rename;
    private final Reset reset;

    public TrackAdapter(
            Context context,
            List<Track> initialTracks,
            OnItemClickListener listener,
            MusicUtility musicUtility,
            AppDatabase database // Pass the database instance
    ) {
        this.context = context;
        this.listener = listener;
        this.database = database;
        this.tracks.addAll(initialTracks);

        this.trim = new Trim(context, musicUtility, database);
        this.rename = new Rename(context, database);
        this.reset = new Reset(context, database);
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
        holder.titleText.setText(current.title);

        holder.itemView.setOnClickListener(v -> listener.onItemClick(current));

        holder.overflowIcon.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, holder.overflowIcon);
            popup.inflate(R.menu.track_item_menu);

            // Hide the "remove" option if we are in the main "All Tracks" list.
            if (currentPlaylistName.equals(MainActivity.ALL_TRACKS_PLAYLIST_NAME)) {
                popup.getMenu().findItem(R.id.action_remove).setVisible(false);
            }

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_add_to_playlist) {
                    showPlaylistSelectionDialog(current);
                    return true;
                } else if (id == R.id.edit) {
                    trim.showTrimDialog(current);
                    return true;
                } else if (id == R.id.action_rename) {
                    // Note: The Rename class should be updated to also modify the database entry.
                    rename.showRenameDialog(current, position, tracks, this::updateList);
                    return true;
                } else if (id == R.id.action_reset) {
                    reset.reset(current);
                    return true;
                } else if (id == R.id.action_remove) {
                    removeTrackFromCurrentPlaylist(current, holder.getAdapterPosition());
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    private void removeTrackFromCurrentPlaylist(Track track, int position) {
        executor.execute(() -> {
            // Perform database operation in the background
            database.playlistDao().removeTrackFromPlaylist(currentPlaylistName, track.id);

            // Update UI on the main thread
            handler.post(() -> {
                if (position != RecyclerView.NO_POSITION) {
                    tracks.remove(position);
                    notifyItemRemoved(position);
                    Toast.makeText(context, "Removed from " + currentPlaylistName, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /**
     * Shows a dialog with a list of available playlists to add a track to.
     * Fetches playlists from the database.
     *
     * @param track The track to be added.
     */
    private void showPlaylistSelectionDialog(Track track) {
        executor.execute(() -> {
            // Fetch playlist names from the database in the background
            List<String> playlistNames = database.playlistDao().getAllPlaylistNames();

            // Switch back to the main thread to show the dialog
            handler.post(() -> {
                if (playlistNames.isEmpty()) {
                    Toast.makeText(context, "No playlists created yet.", Toast.LENGTH_SHORT).show();
                    return;
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        context,
                        android.R.layout.simple_list_item_1,
                        playlistNames
                );

                new AlertDialog.Builder(context)
                        .setTitle("Add to playlist...")
                        .setAdapter(adapter, (dialog, which) -> {
                            String selectedPlaylist = playlistNames.get(which);
                            addTrackToPlaylist(selectedPlaylist, track);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        });
    }

    /**
     * Adds a track to a specified playlist in the database.
     */
    private void addTrackToPlaylist(String playlistName, Track track) {
        executor.execute(() -> {
            // Get the playlist ID from its name
            Playlist playlist = database.playlistDao().getPlaylistByName(playlistName);
            if (playlist != null) {
                // Create the relationship entry
                PlaylistTrackCrossRef crossRef = new PlaylistTrackCrossRef();
                crossRef.playlistId = playlist.id;
                crossRef.trackId = track.id;

                // Insert it into the database
                database.playlistDao().insertPlaylistTrackCrossRef(crossRef);

                handler.post(() -> Toast.makeText(context, "Added to " + playlistName, Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * Call this from MainActivity when the displayed playlist changes.
     */
    public void setCurrentPlaylistName(String playlistName) {
        this.currentPlaylistName = playlistName;
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    public void updateList(List<Track> newList) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new TrackDiffCallback(this.tracks, newList));
        this.tracks.clear();
        this.tracks.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }
}