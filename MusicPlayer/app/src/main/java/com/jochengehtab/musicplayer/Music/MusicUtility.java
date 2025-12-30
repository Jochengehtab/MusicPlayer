package com.jochengehtab.musicplayer.Music;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.jochengehtab.musicplayer.data.Track;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class MusicUtility {
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelToken = new AtomicBoolean(false);
    private final Consumer<String> updateBottomTitle;
    private final Runnable updateBottomPlayIcon;

    private MediaPlayer mediaPlayer;
    private boolean loopEnabled = false;
    private boolean mixEnabled = false;
    private List<Track> playQueue = new ArrayList<>();
    private int currentIndex = 0;
    private OnPlaybackStateListener onPlaybackStateListener;

    public MusicUtility(Context context, Consumer<String> updateBottomTitle, Runnable updateBottomPlayIcon) {
        this.context = context;
        this.updateBottomTitle = updateBottomTitle;
        this.updateBottomPlayIcon = updateBottomPlayIcon;
    }

    public void play(Track track, OnPlaybackStateListener uiListener, long... timespan) {
        this.onPlaybackStateListener = uiListener;
        setupQueue(Collections.singletonList(track), false);
        playCurrentQueueItem(timespan);
    }

    public synchronized void playList(List<Track> musicFiles, boolean shouldMix) {
        if (musicFiles == null || musicFiles.isEmpty()) {
            Toast.makeText(context, "Playlist is empty.", Toast.LENGTH_SHORT).show();
            return;
        }

        // No specific UI listener for playlist mode
        this.onPlaybackStateListener = null;
        setupQueue(musicFiles, shouldMix);
        playCurrentQueueItem();
    }

    private void setupQueue(List<Track> tracks, boolean shouldMix) {
        cancelToken.set(false);
        mixEnabled = shouldMix;
        loopEnabled = false;

        playQueue = new ArrayList<>(tracks);
        if (shouldMix) {
            Collections.shuffle(playQueue);
        }
        currentIndex = 0;
    }

    private synchronized void playCurrentQueueItem(long... timespan) {
        if (cancelToken.get()) return;

        // Boundary Check
        if (currentIndex >= playQueue.size()) {
            if (loopEnabled && !playQueue.isEmpty()) {
                currentIndex = 0; // Loop back to start
            } else {
                // End of Playlist
                stopAndCancel();
                updateBottomPlayIcon.run();
                return;
            }
        }

        Track track = playQueue.get(currentIndex);
        updateBottomTitle.accept(track.title);

        // Prepare Media Player
        if (isInitialized()) mediaPlayer.release();
        mediaPlayer = new MediaPlayer();

        long startMs = (timespan != null && timespan.length >= 1) ? timespan[0] : track.startTime;
        long endMs = (timespan != null && timespan.length >= 2) ? timespan[1] : track.endTime;
        long durationMs = endMs - startMs;

        try {
            mediaPlayer.setDataSource(context, Uri.fromFile(new File(track.uri)));
            mediaPlayer.setOnPreparedListener(mp -> mp.seekTo((int) startMs));

            // Handle Completion and prepare the next Track
            mediaPlayer.setOnSeekCompleteListener(mp -> {
                mp.start();
                notifyListener(true); // Started

                // Auto-stop/next logic after duration
                handler.postDelayed(() -> {
                    // Check if we are still playing the SAME track instance
                    if (mediaPlayer == mp && mp.isPlaying()) {
                        mp.pause();
                        notifyListener(false); // Stopped

                        // Logic for what happens next
                        onTrackFinished();
                    }
                }, durationMs);
            });

            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            notifyListener(false);
        }
    }

    /**
     * Handles the index logic when a song ends.
     */
    private void onTrackFinished() {
        if (cancelToken.get()) return;

        // Only increment if we are NOT looping specific song
        if (!loopEnabled) {
            currentIndex++;
        }

        // Recursively play next
        playCurrentQueueItem();
    }

    private void notifyListener(boolean isStarted) {
        if (onPlaybackStateListener != null) {
            if (isStarted) onPlaybackStateListener.onPlaybackStarted();
            else onPlaybackStateListener.onPlaybackStopped();
        }
    }

    public boolean toggleLoop() {
        loopEnabled = !loopEnabled;
        return loopEnabled;
    }

    public synchronized void stopAndCancel() {
        cancelToken.set(true);
        handler.removeCallbacksAndMessages(null);
        if (isInitialized()) {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        mixEnabled = false;
        notifyListener(false);
    }

    public void pause() {
        if (isInitialized() && mediaPlayer.isPlaying()) mediaPlayer.pause();
    }

    public void resume() {
        if (isInitialized() && !mediaPlayer.isPlaying()) return;
        //TODO proper resume with correct start and end
    }

    public synchronized Track getCurrentTrack() {
        if (playQueue.isEmpty() || currentIndex >= playQueue.size()) return null;
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