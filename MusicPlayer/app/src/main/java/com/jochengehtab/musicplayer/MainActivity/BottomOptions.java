package com.jochengehtab.musicplayer.MainActivity;

import android.content.Context;
import android.widget.ImageButton;
import android.widget.PopupMenu;

import com.jochengehtab.musicplayer.Music.MusicUtility;
import com.jochengehtab.musicplayer.R;

public class BottomOptions {
    private final Context context;
    private final MusicUtility musicUtility;
    private String playListName;

    public BottomOptions(Context context, MusicUtility musicUtility) {
        this.context = context;
        this.musicUtility = musicUtility;
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
                    musicUtility.handleLooping();
                    item.setChecked(true);
                    return true;
                }
                else if (id == R.id.action_mix) {
                    musicUtility.handleMix(playListName);
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