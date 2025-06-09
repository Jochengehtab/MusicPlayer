package com.jochengehtab.musicplayer.Music;

import static com.jochengehtab.musicplayer.MainActivity.timestampsConfig;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

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
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = new MediaPlayer();

        // Try to fetch saved start/end in seconds
        Integer[] timestamps = timestampsConfig.readArray(
                FileManager.getUriHash(uri), Integer[].class
        );

        try {
            mediaPlayer.setDataSource(context, uri);

            if (timestamps != null && timestamps.length > 1) {
                // Trimmed playback
                final int startMs = timestamps[0] * 1000;
                final int durationMs = (timestamps[1] - timestamps[0]) * 1000;

                mediaPlayer.setOnPreparedListener(mp -> mp.seekTo(startMs));
                mediaPlayer.setOnSeekCompleteListener(mp -> {
                    mp.start();
                    listener.onPlaybackStarted();

                    // Stop after the trimmed duration
                    handler.postDelayed(() -> {
                        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                            mediaPlayer.pause();
                        }
                        listener.onPlaybackStopped();
                    }, durationMs);
                });
                mediaPlayer.prepareAsync();

            } else {
                // Full‐track playback
                mediaPlayer.setOnPreparedListener(mp -> {
                    mp.start();
                    listener.onPlaybackStarted();
                });
                mediaPlayer.setOnCompletionListener(mp -> listener.onPlaybackStopped());
                mediaPlayer.prepare();
            }

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

    public int getTrackDuration(Uri uri) {
        int durationMs;
        // Determine track duration in seconds
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(context, uri);
            String durationStr = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION);
            durationMs = Integer.parseInt(Objects.requireNonNull(durationStr));
            try {
                retriever.release();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
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
