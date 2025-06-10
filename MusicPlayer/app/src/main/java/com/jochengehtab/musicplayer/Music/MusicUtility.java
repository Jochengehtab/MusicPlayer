package com.jochengehtab.musicplayer.Music;

import static com.jochengehtab.musicplayer.MainActivity.timestampsConfig;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.jochengehtab.musicplayer.Utility.FileManager;

import java.io.IOException;
import java.util.Objects;

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
        if (mediaPlayer != null) mediaPlayer.release();
        mediaPlayer = new MediaPlayer();

        Integer[] timestamps = timestampsConfig.readArray(
                FileManager.getUriHash(uri), Integer[].class
        );

        try {
            mediaPlayer.setDataSource(context, uri);

            boolean trimmed = timestamps != null && timestamps.length > 1;
            final int startMs = trimmed ? timestamps[0] * 1000 : 0;
            final int durationMs = trimmed ? (timestamps[1] - timestamps[0]) * 1000 : 0;

            if (startMs == durationMs) {
                Toast.makeText(context, "Start and End time are the same!", Toast.LENGTH_SHORT).show();
            }

            mediaPlayer.setOnPreparedListener(mp -> {
                if (trimmed) {
                    mp.seekTo(startMs);
                } else {
                    mp.start();
                    listener.onPlaybackStarted();
                }
            });

            mediaPlayer.setOnSeekCompleteListener(mp -> {
                mp.start();
                listener.onPlaybackStarted();
                handler.postDelayed(() -> {
                    // Only act if the MediaPlayer for this task is still the active one.
                    if (mp != this.mediaPlayer) {
                        return; // This player is stale, do nothing.
                    }

                    if (mp.isPlaying()) {
                        mp.pause();
                    }
                    listener.onPlaybackStopped();
                }, durationMs);
            });

            if (!trimmed) {
                mediaPlayer.setOnCompletionListener(mp -> listener.onPlaybackStopped());
            }

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
        if (mediaPlayer != null) {
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
        if (mediaPlayer == null) {
            throw new IllegalStateException("No MediaPlayer is prepared. Call play() first.");
        }

        // 1) Cancel any pending pause callbacks from play()
        handler.removeCallbacksAndMessages(null);

        // 2) Remove the old OnSeekCompleteListener so it won't schedule new pauses
        mediaPlayer.setOnSeekCompleteListener(null);

        // 3) Install our loop‐on‐completion listener
        mediaPlayer.setOnCompletionListener(mp -> {
            listener.onPlaybackStopped();
            mp.seekTo(0);
            mp.start();
            listener.onPlaybackStarted();
        });

        // 4) Kick off playback if it isn't already running
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            listener.onPlaybackStarted();
        }
    }


    public int getTrackDuration(Uri uri) {
        int durationMs;
        // Determine track duration in seconds
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(context, uri);
            durationMs = Integer.parseInt(Objects.requireNonNull(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION)));
            retriever.release();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return durationMs / 1000;
    }

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    /** Resume if paused. */
    public void resume() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    /**
     * Returns true if the internal MediaPlayer exists and is currently playing.
     */
    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    /**
     * Returns true if the internal MediaPlayer exists and is paused (i.e. not playing).
     */
    public boolean isPaused() {
        return mediaPlayer != null && !mediaPlayer.isPlaying();
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
