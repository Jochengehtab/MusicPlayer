package com.jochengehtab.savemanger;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;

import java.io.IOException;

public class MusicUtility {
    private final Context ctx;
    private MediaPlayer mediaPlayer;

    public MusicUtility(Context context) {
        this.ctx = context;
    }

    public void play(Uri uri) {
        // Release any existing player
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        MusicPlayer.canResume = false;

        mediaPlayer = new MediaPlayer();
        try {
            // Set data source to content URI
            mediaPlayer.setDataSource(ctx, uri);
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.prepare();
            mediaPlayer.setOnCompletionListener(mp -> MusicPlayer.canResume = true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Stop playback and release resources.
     */
    public void free() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
