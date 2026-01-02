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
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String playListName;

    public BottomOptions(Context context, MusicUtility musicUtility, AppDatabase database) {
        this.context = context;
        this.musicUtility = musicUtility;
        this.database = database;
    }

    public void handleBottomOptions(ImageButton bottomOptionsButton) {
        bottomOptionsButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, bottomOptionsButton);
            popup.inflate(R.menu.bottom_bar_menu);

            popup.getMenu().setGroupCheckable(R.id.group_playback_modes, true, true);

            // Check current state to set the tick mark in the UI
            if (musicUtility.isLooping()) {
                popup.getMenu().findItem(R.id.action_loop).setChecked(true);
            } else if (musicUtility.isMixing()) {
                popup.getMenu().findItem(R.id.action_mix).setChecked(true);
            }

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_loop) {
                    boolean isNowLooping = musicUtility.toggleLoop();
                    return true;
                }
                else if (id == R.id.action_mix) {
                    boolean isSmartActive = musicUtility.toggleSmartMode();
                    item.setChecked(isSmartActive);

                    if (isSmartActive) {
                        Toast.makeText(context, "Smart DJ Enabled: AI will pick next songs", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context, "Smart DJ Disabled", Toast.LENGTH_SHORT).show();
                    }

                    // TODO
                    //startShuffledPlayback();
                    return true;
                }
                return false;
            });

            popup.show();
        });
    }

    private void startShuffledPlayback() {
        if (playListName == null || playListName.isEmpty()) {
            Toast.makeText(context, "No playlist selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            List<Track> playlistTracks;
            if (playListName.equals(ALL_TRACKS_PLAYLIST_NAME)) {
                playlistTracks = database.trackDao().getAllTracks();
            } else {
                PlaylistWithTracks pwt = database.playlistDao().getPlaylistWithTracks(playListName);
                playlistTracks = (pwt != null) ? pwt.tracks : new ArrayList<>();
            }

            handler.post(() -> {
                if (playlistTracks.isEmpty()) {
                    Toast.makeText(context, "Playlist is empty, cannot shuffle.", Toast.LENGTH_SHORT).show();
                    return;
                }
                musicUtility.playList(playlistTracks, true);
            });
        });
    }

    public void setPlaylistName(String playlistName) {
        this.playListName = playlistName;
    }
}