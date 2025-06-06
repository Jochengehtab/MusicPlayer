package com.jochengehtab.musicplayer.Music;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;

import java.io.IOException;

public class MusicUtility {
    private final Context ctx;
    private MediaPlayer mediaPlayer;
    private final Handler handler;

    public interface OnTrackCompleteListener {
        void onTrackComplete();
    }

    public MusicUtility(Context context) {
        this.ctx = context;
        this.handler = new Handler();
    }

    /**
     * Play the entire track from start to finish, no callback.
     */
    public void play(Uri uri) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(ctx, uri);
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnCompletionListener(mp -> { /* no-op */ });
            mediaPlayer.prepare();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Play the entire track but notify listener when it finishes.
     */
    public void play(Uri uri, OnTrackCompleteListener listener) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(ctx, uri);
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnCompletionListener(mp -> listener.onTrackComplete());
            mediaPlayer.prepare();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Play a segment of the given URI from startSec to endSec (in seconds).
     * Stops automatically at endSec.
     */
    public void playSegment(Uri uri, int startSec, int endSec) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(ctx, uri);
            mediaPlayer.setOnPreparedListener(mp -> mp.seekTo(startSec * 1000));
            mediaPlayer.setOnSeekCompleteListener(mp -> {
                mp.start();
                int durationMs = (endSec - startSec) * 1000;
                handler.postDelayed(() -> {
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        mediaPlayer.pause();
                    }
                }, durationMs);
            });
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Stop & release resources.
     */
    public void stopAndRelease() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
