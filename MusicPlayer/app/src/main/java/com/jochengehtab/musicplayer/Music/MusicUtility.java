package com.jochengehtab.musicplayer.Music;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

// Import the new Track entity
import com.jochengehtab.musicplayer.data.Track;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class MusicUtility {
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelToken = new AtomicBoolean(false);
    private final Consumer<String> updateBottomTitle;
    private final Runnable updateBottomPlayIcon;
    private final Random random = new Random();
    private final OnPlaybackStateListener onPlaybackStateListener;
    private MediaPlayer mediaPlayer;
    private boolean loopEnabled = false;
    private boolean mixEnabled = false;
    private List<Track> playQueue = new ArrayList<>();
    private int currentIndex = 0;

    public MusicUtility(Context context, Consumer<String> updateBottomTitle, Runnable updateBottomPlayIcon) {
        this.context = context;
        this.updateBottomPlayIcon = updateBottomPlayIcon;
        this.updateBottomTitle = updateBottomTitle;
        this.onPlaybackStateListener = new OnPlaybackStateListener() {
            @Override
            public void onPlaybackStarted() {
                // This can be used for UI updates if needed
            }

            @Override
            public void onPlaybackStopped() {
                // When a track finishes, automatically play the next one in the queue
                synchronized (MusicUtility.this) {
                    if (cancelToken.get()) {
                        return;
                    }
                    currentIndex++;
                    playNextInQueue();
                }
            }
        };
    }

    /**
     * Plays a track. If the track has been trimmed, it plays the specified segment.
     *
     * @param track    The Track object to play.
     * @param listener A listener to be notified of playback start/stop events.
     * @param timespan Optional. An array of [startTimeMs, endTimeMs] to override the track's saved trim times. Used for previewing.
     */
    public void play(Track track, OnPlaybackStateListener listener, long... timespan) {
        if (isInitialized()) {
            mediaPlayer.release();
        }
        mediaPlayer = new MediaPlayer();

        long startMs;
        long endMs;

        // Check if an override timespan is provided (for previewing in Trim dialog)
        if (timespan != null && timespan.length >= 2) {
            startMs = timespan[0];
            endMs = timespan[1];
        } else {
            // Otherwise, use the start and end times stored in the Track object
            startMs = track.startTime;
            endMs = track.endTime;
        }

        if (startMs >= endMs) {
            Toast.makeText(context, "Invalid trim times. Start must be before end.", Toast.LENGTH_SHORT).show();
            if (listener != null) listener.onPlaybackStopped();
            return;
        }

        try {
            mediaPlayer.setDataSource(context, Uri.parse(track.uri));
            mediaPlayer.setOnPreparedListener(mp -> mp.seekTo((int) startMs));
            mediaPlayer.setOnSeekCompleteListener(mp -> {
                mp.start();
                if (listener != null) {
                    listener.onPlaybackStarted();
                }

                // Schedule a stop command for when the trimmed segment should end
                long durationMs = endMs - startMs;
                handler.postDelayed(() -> {
                    // Check if this player is still the active one
                    if (mediaPlayer == mp && mp.isPlaying()) {
                        mp.pause(); // Use pause instead of stop for smoother transitions
                    }
                    if (listener != null) {
                        listener.onPlaybackStopped();
                    }
                }, durationMs);
            });

            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            Toast.makeText(context, "Error playing track: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (listener != null) listener.onPlaybackStopped();
        }
    }

    /**
     * Plays a given list of tracks.
     *
     * @param musicFiles The list of tracks to play.
     * @param shouldMix  If true, the playlist will be shuffled before playing.
     */
    public synchronized void playList(List<Track> musicFiles, boolean shouldMix) {
        if (musicFiles == null || musicFiles.isEmpty()) {
            Toast.makeText(context, "Playlist is empty.", Toast.LENGTH_SHORT).show();
            return;
        }
        cancelToken.set(false);
        mixEnabled = shouldMix;
        loopEnabled = false;

        playQueue = new ArrayList<>(musicFiles);

        if (shouldMix) {
            Collections.shuffle(playQueue, random);
        }

        currentIndex = 0;
        playNextInQueue();
    }

    private synchronized void playNextInQueue() {
        if (cancelToken.get()) {
            updateBottomPlayIcon.run();
            return;
        }

        if (currentIndex >= playQueue.size()) {
            // End of playlist reached
            if (loopEnabled) {
                currentIndex = 0; // Loop back to the start
            } else {
                mixEnabled = false;
                updateBottomPlayIcon.run();
                return; // Stop playback
            }
        }

        Track nextTrack = playQueue.get(currentIndex);
        updateBottomTitle.accept(nextTrack.title);
        play(nextTrack, onPlaybackStateListener);
    }

    public boolean toggleLoop() {
        loopEnabled = !loopEnabled;
        return loopEnabled;
    }

    public synchronized void stopAndCancel() {
        cancelToken.set(true);
        handler.removeCallbacksAndMessages(null); // Cancel any pending stop commands
        if (isInitialized()) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        mixEnabled = false;
    }

    public void pause() {
        if (isInitialized() && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    public void resume() {
        if (isInitialized() && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    public synchronized Track getCurrentTrack() {
        if (playQueue.isEmpty() || currentIndex >= playQueue.size()) {
            return null;
        }
        return playQueue.get(currentIndex);
    }

    public boolean isPlaying() {
        return isInitialized() && mediaPlayer.isPlaying();
    }

    public boolean isLooping() {
        return loopEnabled;
    }

    public boolean isMixing() {
        return mixEnabled;
    }

    private boolean isInitialized() {
        return mediaPlayer != null;
    }
}