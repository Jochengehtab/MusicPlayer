package com.jochengehtab.musicplayer.Music;

import static com.jochengehtab.musicplayer.MainActivity.MainActivity.timestampsConfig;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.jochengehtab.musicplayer.MainActivity.MainActivity;
import com.jochengehtab.musicplayer.MusicList.Track;
import com.jochengehtab.musicplayer.Utility.FileManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class MusicUtility {
    private MediaPlayer mediaPlayer;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelToken = new AtomicBoolean(false);
    private final Consumer<String> updateBottomTitle;
    private final Runnable updateBottomPlayIcon;

    private boolean loopEnabled = false;
    private boolean mixEnabled = false;
    private List<Track> playQueue = new ArrayList<>();
    private int currentIndex = 0;
    private final Random random = new Random();

    public MusicUtility(Context context, Consumer<String> updateBottomTitle, Runnable updateBottomPlayIcon) {
        this.context = context;
        this.updateBottomPlayIcon = updateBottomPlayIcon;
        this.updateBottomTitle = updateBottomTitle;
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

        // Try to read existing timestamps
        Integer[] timestamps = timestampsConfig.readArray(
                FileManager.getUriHash(uri), Integer[].class
        );

        // If timestamps don't exist, this is a new track.
        // Calculate duration and write default timestamps now.
        if (timestamps == null) {
            try {
                // This is the expensive call, but now it only happens once per new track.
                final int duration = getTrackDuration(uri);
                // Create the default timestamp array [start, end, total_duration]
                timestamps = new Integer[]{0, duration, duration};
                // Write it to the config for all future plays
                timestampsConfig.write(FileManager.getUriHash(uri), timestamps);
            } catch (RuntimeException e) {
                Toast.makeText(context, "Error getting track info: " + e.getMessage(), Toast.LENGTH_LONG).show();
                // Abort playback if we can't get info
                if (mediaPlayer != null) {
                    mediaPlayer.release();
                    mediaPlayer = null;
                }
                if (listener != null) {
                    // Notify UI that nothing is playing
                    listener.onPlaybackStopped();
                }
                return;
            }
        }

        // Now we are guaranteed to have timestamps.
        int startSeconds = timestamps[0];
        int endSeconds = timestamps[1];

        if (startSeconds == endSeconds) {
            Toast.makeText(context,
                    "Start and End time are the same!", Toast.LENGTH_SHORT).show();
            // Abort playback
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
            if (listener != null) {
                listener.onPlaybackStopped();
            }
            return;
        }

        // We can now reliably call playSegment for all cases
        playSegment(uri, startSeconds, endSeconds, listener);
    }

    public void playSegment(Uri uri, int startSec, int endSec, OnPlaybackStateListener listener) {
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
                // Notify the listener that playback has started
                if (listener != null) {
                    listener.onPlaybackStarted();
                }

                int durationMs = (endSec - startSec) * 1000;
                handler.postDelayed(() -> {
                    if (mediaPlayer != this.mediaPlayer) {
                        return; // This player is stale, do nothing
                    }

                    if (this.mediaPlayer.isPlaying()) {
                        this.mediaPlayer.pause();
                    }

                    // Notify the listener that playback has stopped
                    if (listener != null) {
                        listener.onPlaybackStopped();
                    }

                }, durationMs);
            });

            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            throw new RuntimeException(e);
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

    public synchronized void playMix(List<Track> musicFiles) {
        cancelToken.set(false);

        mixEnabled = true;
        MainActivity.isMixPlaying = true;
        loopEnabled = false;

        playQueue = new ArrayList<>(musicFiles);
        Collections.shuffle(playQueue, random);
        currentIndex = 0;

        playNextInQueue();
    }

    /**
     * Plays a given list of tracks in order.
     *
     * @param musicFiles The list of tracks to play.
     */
    public synchronized void playList(List<Track> musicFiles) {
        if (musicFiles == null || musicFiles.isEmpty()) {
            return;
        }
        cancelToken.set(false);

        mixEnabled = true; // Treat as a "mix" for playback control
        MainActivity.isMixPlaying = true;
        loopEnabled = false;

        playQueue = new ArrayList<>(musicFiles); // No shuffle
        currentIndex = 0;

        playNextInQueue();
    }


    private synchronized void playNextInQueue() {
        if (cancelToken.get()) {
            MainActivity.isMixPlaying = false;
            updateBottomPlayIcon.run();
            return;
        }
        if (currentIndex >= playQueue.size()) {
            mixEnabled = false;
            MainActivity.isMixPlaying = false;
            updateBottomPlayIcon.run();
            if (!loopEnabled) {
                return;
            }
            currentIndex = 0;
        }

        Track nextTrack = playQueue.get(currentIndex);
        updateBottomTitle.accept(nextTrack.title());
        play(nextTrack.uri(), new OnPlaybackStateListener() {
            @Override
            public void onPlaybackStarted() {
            }

            @Override
            public void onPlaybackStopped() {
                // We lock M.this to prevent race condition
                synchronized (MusicUtility.this) {
                    if (cancelToken.get()) {
                        return;
                    }
                    currentIndex++;
                    playNextInQueue();
                }
            }
        });
    }


    public boolean toggleLoop() {
        loopEnabled = !loopEnabled;
        return loopEnabled;
    }

    public synchronized void cancelMix() {
        cancelToken.set(true);
        mixEnabled = false;
        MainActivity.isMixPlaying = false;
    }

    public synchronized Track getCurrentTitle() {
        if (playQueue.isEmpty() || currentIndex >= playQueue.size()) {
            return null;
        }
        return playQueue.get(currentIndex);
    }

    public synchronized void stopAndCancel() {
        cancelToken.set(true);
        stopAndRelease();
        mixEnabled = false;
        MainActivity.isMixPlaying = false;
    }

    public void pause() {
        if (isInitialized() && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

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

    public boolean isLooping() {
        return loopEnabled;
    }

    public boolean isMixing() {
        return mixEnabled;
    }

    /**
     * Resume if paused.
     */
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
