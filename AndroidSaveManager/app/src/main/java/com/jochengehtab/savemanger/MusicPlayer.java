package com.jochengehtab.savemanger;


import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;

import java.io.IOException;
import java.util.List;

public class MusicPlayer {
    private final Context ctx;
    private MediaPlayer mediaPlayer;
    private List<Uri> playlist;
    private int currentIndex = 0;

    public MusicPlayer(Context context) {
        this.ctx = context;
    }

    /**
     * Assign the list of track URIs to play.
     */
    public void setPlaylist(List<Uri> uris) {
        this.playlist = uris;
        this.currentIndex = 0;
    }

    /**
     * Start playing the first track.
     */
    public void playMusic() {
        if (playlist == null || playlist.isEmpty()) return;
        playUri(playlist.get(currentIndex));
    }

    /**
     * Internal helper: play one URI, then advance on completion.
     */
    private void playUri(Uri uri) {
        // Release any existing player
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }

        mediaPlayer = new MediaPlayer();
        try {
            // Set data source to content URI
            mediaPlayer.setDataSource(ctx, uri);
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        mediaPlayer.setOnCompletionListener(mp -> {
            currentIndex = (currentIndex + 1) % playlist.size();
            playUri(playlist.get(currentIndex));
        });
    }

    /**
     * Stop playback and release resources.
     */
    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
