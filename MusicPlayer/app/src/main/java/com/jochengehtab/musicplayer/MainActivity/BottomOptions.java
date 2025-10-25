package com.jochengehtab.musicplayer.MainActivity;

import static com.jochengehtab.musicplayer.MainActivity.MainActivity.ALL_TRACKS_PLAYLIST_NAME;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.jochengehtab.musicplayer.Music.MusicUtility;
import com.jochengehtab.musicplayer.R;
import com.jochengehtab.musicplayer.data.AppDatabase;
import com.jochengehtab.musicplayer.data.PlaylistWithTracks;
import com.jochengehtab.musicplayer.data.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BottomOptions {
    private final Context context;
    private final MusicUtility musicUtility;
    private final AppDatabase database;
    private String playListName;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public BottomOptions(Context context, MusicUtility musicUtility, AppDatabase database) {
        this.context = context;
        this.musicUtility = musicUtility;
        this.database = database;
    }

    public void handleBottomOptions(ImageButton bottomOptionsButton, ImageButton bottomPlay, TextView bottomTitle) {
        bottomOptionsButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, bottomOptionsButton);
            popup.inflate(R.menu.bottom_bar_menu);

            // 1. Set the group to be single-choice
            popup.getMenu().setGroupCheckable(R.id.group_playback_modes, true, true);

            // 2. Pre-check the currently active mode
            if (musicUtility.isLooping()) {
                popup.getMenu().findItem(R.id.action_loop).setChecked(true);
            } else if (musicUtility.isMixing()) {
                popup.getMenu().findItem(R.id.action_mix).setChecked(true);
            }

            // 3. Handle menu item selections
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_loop) {
                    boolean isNowLooping = musicUtility.toggleLoop();
                    item.setChecked(isNowLooping);
                    // Uncheck the other option if this one is now active
                    if (isNowLooping) {
                        popup.getMenu().findItem(R.id.action_mix).setChecked(false);
                    }
                    return true;

                } else if (id == R.id.action_mix) {
                    // Start shuffle playback on a background thread
                    startShuffledPlayback(bottomPlay, bottomTitle);
                    item.setChecked(true);
                    // Uncheck the other option
                    popup.getMenu().findItem(R.id.action_loop).setChecked(false);
                    return true;
                }
                return false;
            });

            popup.show();
        });
    }

    /**
     * Fetches the current playlist from the database and starts shuffled playback.
     */
    private void startShuffledPlayback(ImageButton bottomPlay, TextView bottomTitle) {
        if (playListName == null || playListName.isEmpty()) {
            Toast.makeText(context, "No playlist selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            // Fetch tracks from the database in the background
            List<Track> playlistTracks;
            if (playListName.equals(ALL_TRACKS_PLAYLIST_NAME)) {
                playlistTracks = database.trackDao().getAllTracks();
            } else {
                PlaylistWithTracks pwt = database.playlistDao().getPlaylistWithTracks(playListName);
                playlistTracks = (pwt != null) ? pwt.tracks : new ArrayList<>();
            }

            // Switch to the main thread to start playback and update UI
            handler.post(() -> {
                if (playlistTracks.isEmpty()) {
                    Toast.makeText(context, "Playlist is empty, cannot shuffle.", Toast.LENGTH_SHORT).show();
                    return;
                }

                musicUtility.playList(playlistTracks, true); // true for shuffle

                // Update the UI to reflect playback has started
                bottomPlay.setImageResource(R.drawable.ic_stop_white_24dp);
                Track currentTrack = musicUtility.getCurrentTrack();
                if (currentTrack != null) {
                    bottomTitle.setText(currentTrack.title);
                }
            });
        });
    }

    public void setPlaylistName(String playlistName) {
        this.playListName = playlistName;
    }
}