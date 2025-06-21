package com.jochengehtab.musicplayer.MainActivity;

import android.content.Context;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.jochengehtab.musicplayer.Music.MusicPlayer;
import com.jochengehtab.musicplayer.Music.MusicUtility;
import com.jochengehtab.musicplayer.Music.OnPlaybackStateListener;
import com.jochengehtab.musicplayer.R;
import com.jochengehtab.musicplayer.Utility.FileManager;

import java.util.function.Consumer;

public class BottomOptions {
    private final Context context;

    private final MusicPlayer musicPlayer;
    private final MusicUtility musicUtility;
    private final FileManager fileManager;

    public BottomOptions(Context context, MusicUtility musicUtility, MusicPlayer musicPlayer, FileManager fileManager) {
        this.context = context;
        this.musicUtility = musicUtility;
        this.musicPlayer = musicPlayer;
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
            int checkedId = musicPlayer.isLooping()
                    ? R.id.action_loop
                    : musicPlayer.isMixing()
                    ? R.id.action_mix
                    : -1;

            if (checkedId != -1) {
                popup.getMenu().findItem(checkedId).setChecked(true);
            }

            // 3) Handle selections
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_loop) {
                    boolean looping = musicPlayer.toggleLoop();
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
                        musicPlayer.playMix(fileManager.loadMusicFiles());
                        bottomPlay.setImageResource(R.drawable.ic_stop_white_24dp);
                        bottomTitle.setText(musicPlayer.getCurrentTitle().title());
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
}
