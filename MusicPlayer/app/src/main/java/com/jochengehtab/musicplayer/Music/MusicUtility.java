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

    public interface OnPlaybackStateListener {
        void onPlaybackStarted();
        void onPlaybackStopped();
    }

    /**
     * Play the entire track, notifying the listener when start/stop occur.
     */

    // TODO unify this two play functions into one so that the trim option also works
    public void play(Uri uri, OnPlaybackStateListener listener) {
        // Release any existing player
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(context, uri);
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                listener.onPlaybackStarted();
            });
            mediaPlayer.setOnCompletionListener(mp -> listener.onPlaybackStopped());
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

        Integer[] timestamps = timestampsConfig.readArray(FileManager.getUriHash(uri), Integer[].class);

        if (timestamps.length > 1) {
            playSegment(uri, timestamps[0], timestamps[1]);
            return;
        }
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(context, uri);
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
