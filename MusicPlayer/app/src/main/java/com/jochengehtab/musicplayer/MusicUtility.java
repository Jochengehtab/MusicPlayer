package com.jochengehtab.musicplayer;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;

import java.io.IOException;

public class MusicUtility {
    private final Context ctx;
    private MediaPlayer mediaPlayer;

    public interface OnTrackCompleteListener {
        void onTrackComplete();
    }

    public MusicUtility(Context context) {
        this.ctx = context;
    }

    /**
     * Plays exactly one URI. As soon as that URI finishes, calls listener.onTrackComplete().
     */
    public void play(Uri uri, OnTrackCompleteListener listener) {
        // Release any previous player
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        // Create a fresh MediaPlayer
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(ctx, uri);
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnCompletionListener(mp -> {
                // When this track ends, notify the listener
                listener.onTrackComplete();
            });
            mediaPlayer.prepare();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Stop & release immediately.
     */
    public void stopAndRelease() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
