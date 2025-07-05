package com.jochengehtab.musicplayer.Music;

import static com.jochengehtab.musicplayer.MainActivity.MainActivity.timestampsConfig;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.jochengehtab.musicplayer.Utility.FileManager;

import java.io.IOException;

public class MusicUtility {
    private final Context context;
    private MediaPlayer mediaPlayer;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public MusicUtility(Context context) {
        this.context = context;
    }

    /**
     * Play either the trimmed segment (if timestamps exist) or the full track.
     * Notifies the single listener when playback starts and when it ends.
     */
    public void play(Uri uri, OnPlaybackStateListener listener) {
        if (isInitialized()) {
            mediaPlayer.release();
        }

        mediaPlayer = new MediaPlayer();

        Integer[] timestamps = timestampsConfig.readArray(
                FileManager.getUriHash(uri), Integer[].class
        );

        if (timestamps != null) {
            int startSeconds = timestamps[0];
            int endSeconds = timestamps[1];

            if (startSeconds == endSeconds) {
                Toast.makeText(context,
                        "Start and End time are the same!", Toast.LENGTH_SHORT).show();
                return;
            }
            playSegment(uri, startSeconds, endSeconds);
            return;
        }

        try {
            // Since no custom start was found we start from the beginning
            int startMs = 0;

            // Get the duration of the track
            int durationMs = getTrackDuration(uri) * 1000;

            mediaPlayer.setDataSource(context, uri);

            mediaPlayer.setOnPreparedListener(mediaPlayer -> mediaPlayer.seekTo(startMs));

            mediaPlayer.setOnSeekCompleteListener(mediaPlayer -> {
                mediaPlayer.start();
                listener.onPlaybackStarted();
                handler.postDelayed(() -> {
                    // Only act if the MediaPlayer for this task is still the active one.
                    if (mediaPlayer != this.mediaPlayer) {
                        // This player is stale, do nothing.
                        return;
                    }

                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.pause();
                    }
                    listener.onPlaybackStopped();
                }, durationMs);
            });

            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Play a segment of the given URI from startSec to endSec (in seconds).
     * Stops automatically at endSec.
     */
    public void playSegment(Uri uri, int startSec, int endSec) {
        if (isInitialized()) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        mediaPlayer = new MediaPlayer();

        try {
            mediaPlayer.setDataSource(context, uri);
            mediaPlayer.setOnPreparedListener(mediaPlayer -> mediaPlayer.seekTo(startSec * 1000));
            mediaPlayer.setOnSeekCompleteListener(mediaPlayer -> {
                mediaPlayer.start();
                int durationMs = (endSec - startSec) * 1000;
                handler.postDelayed(() -> {
                    // Note: The captured lambda variable is also named 'mediaPlayer'
                    // We compare it to the class instance 'this.mediaPlayer'
                    if (mediaPlayer != this.mediaPlayer) {
                        return; // This player is stale, do nothing
                    }

                    if (this.mediaPlayer.isPlaying()) {
                        this.mediaPlayer.pause();
                    }
                }, durationMs);
            });

            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Make whatever is currently loaded loop forever.
     * When the track ends, onPlaybackStopped() is called,
     * then the track is rewound and onPlaybackStarted() is called again.
     */
    public void loopMediaPlayer(OnPlaybackStateListener listener) {
        if (!isInitialized()) {
            throw new IllegalStateException("No MediaPlayer is prepared. Call play() first.");
        }

        // Cancel any pending pause callbacks from play()
        handler.removeCallbacksAndMessages(null);

        // Remove the old OnSeekCompleteListener so it won't schedule new pauses
        mediaPlayer.setOnSeekCompleteListener(null);

        // Install our loop‐on‐completion listener so that we reach the end we start over again
        mediaPlayer.setOnCompletionListener(mp -> {
            listener.onPlaybackStopped();
            mp.seekTo(0);
            mp.start();
            listener.onPlaybackStarted();
        });

        // Kick off playback if it isn't already running
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            listener.onPlaybackStarted();
        }
    }

    public int getTrackDuration(Uri uri) {
        try {
            MediaPlayer mediaPlayer = MediaPlayer.create(context, uri);
            if (mediaPlayer != null) {
                int durationMs = mediaPlayer.getDuration();
                mediaPlayer.release();
                return (durationMs + 500) / 1000;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve duration", e);
        }

        throw new RuntimeException("Found no length for URI: " + uri);
    }

    public void pause() {
        if (isInitialized() && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    /** Resume if paused. */
    public void resume() {
        if (isInitialized() && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    /**
     * Returns true if the internal MediaPlayer exists and is currently playing.
     */
    public boolean isPlaying() {
        return isInitialized() && mediaPlayer.isPlaying();
    }

    /**
     * Stop & release resources.
     */
    public void stopAndRelease() {
        if (isInitialized()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    public boolean isInitialized() {
        return mediaPlayer != null;
    }
}
