package com.jochengehtab.musicplayer.MainActivity;

import android.content.Context;
import android.view.MenuItem; // Import MenuItem
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast; // Import Toast

import com.jochengehtab.musicplayer.Music.MusicPlayer;
import com.jochengehtab.musicplayer.Music.MusicUtility;
import com.jochengehtab.musicplayer.Music.OnPlaybackStateListener;
import com.jochengehtab.musicplayer.R;
import com.jochengehtab.musicplayer.Utility.FileManager;

public class BottomOptions {
    private final Context context;
    private final MusicUtility musicUtility;
    private final MusicPlayer musicPlayer;
    private final FileManager fileManager;

    public BottomOptions(Context context, MusicUtility musicUtility, MusicPlayer musicPlayer, FileManager fileManager) {
        this.context = context;
        this.musicUtility = musicUtility;
        this.musicPlayer = musicPlayer;
        this.fileManager = fileManager;
    }

    public void handleBottomOptions(ImageButton bottomOptionsButton, OnPlaybackStateListener playbackListener, ImageButton bottomPlay, TextView bottomTitle) {
        bottomOptionsButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, bottomOptionsButton);
            popup.inflate(R.menu.bottom_bar_menu);

            // --- KEY CHANGE: Update the menu UI before showing it ---
            MenuItem loopItem = popup.getMenu().findItem(R.id.action_loop);
            if (musicPlayer.isLooping()) {
                loopItem.setTitle(R.string.disable_loop);
            } else {
                loopItem.setTitle(R.string.enable_loop);
            }
            // --- END OF KEY CHANGE ---

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_loop) {
                    boolean isNowLooping = musicPlayer.toggleLoop();
                    // Provide feedback to the user
                    Toast.makeText(
                            context,
                            isNowLooping ? "Looping enabled" : "Looping disabled",
                            Toast.LENGTH_SHORT
                    ).show();
                    return true;
                } else if (id == R.id.action_mix) {
                    // ... your existing mix logic ...
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }
}