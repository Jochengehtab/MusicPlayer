package com.jochengehtab.musicplayer.MainActivity;

import android.content.Context;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.jochengehtab.musicplayer.Music.MusicUtility;
import com.jochengehtab.musicplayer.Music.OnPlaybackStateListener;
import com.jochengehtab.musicplayer.MusicList.Track;
import com.jochengehtab.musicplayer.R;
import com.jochengehtab.musicplayer.Utility.FileManager;

import java.util.List;

public class BottomOptions {
    private final Context context;

    private final MusicUtility musicUtility;
    private final FileManager fileManager;
    private String playListName;

    public BottomOptions(Context context, MusicUtility musicUtility, FileManager fileManager) {
        this.context = context;
        this.musicUtility = musicUtility;
        this.fileManager = fileManager;
    }

    public void handleBottomOptions(ImageButton bottomOptions, OnPlaybackStateListener playbackListener, ImageButton bottomPlay, TextView bottomTitle) {
        bottomOptions.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, bottomOptions);
            popup.inflate(R.menu.bottom_bar_menu);

            // 1) Tell the group to be single‐choice
            popup.getMenu().setGroupCheckable(
                    R.id.group_playback_modes,
                    true,
                    true
            );

            // 2) Pre‐check the currently active mode, if any
            int checkedId = musicUtility.isLooping()
                    ? R.id.action_loop
                    : musicUtility.isMixing()
                    ? R.id.action_mix
                    : -1;

            if (checkedId != -1) {
                popup.getMenu().findItem(checkedId).setChecked(true);
            }

            // 3) Handle selections
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_loop) {
                    boolean looping = musicUtility.toggleLoop();
                    // uncheck the other
                    popup.getMenu().findItem(R.id.action_mix).setChecked(false);
                    item.setChecked(looping);
                    if (musicUtility.isInitialized()) {
                        musicUtility.loopMediaPlayer(playbackListener);
                    }
                    return true;

                } else if (id == R.id.action_mix) {
                    if (fileManager != null) {
                        MainActivity.isMixPlaying = true;
                        List<Track> updatedPlaylist = fileManager.loadPlaylistMusicFiles(playListName);
                        musicUtility.playList(updatedPlaylist, true);

                        bottomPlay.setImageResource(R.drawable.ic_stop_white_24dp);
                        Track currentTrack = musicUtility.getCurrentTitle();
                        if (currentTrack != null) {
                            bottomTitle.setText(currentTrack.title());
                        }
                    }
                    popup.getMenu().findItem(R.id.action_loop).setChecked(false);
                    item.setChecked(true);
                    return true;
                }
                return false;
            });

            popup.show();
        });
    }

    public void setPlaylistName(String playlistName) {
        this.playListName = playlistName;
    }
}
