package com.jochengehtab.savemanger;

import android.content.Context;
import android.media.MediaPlayer;

public class MusicPlayer {
    private final MediaPlayer mediaPlayer;

    public MusicPlayer(Context context, int path) {
        mediaPlayer = MediaPlayer.create(context, path);
    }

    public void playMusic() {
        this.mediaPlayer.start();
    }
}
