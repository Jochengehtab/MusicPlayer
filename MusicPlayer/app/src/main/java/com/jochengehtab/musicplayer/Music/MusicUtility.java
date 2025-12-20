package com.jochengehtab.musicplayer.Music;

import android.content.Context;
import android.media.MediaPlayer;
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

    // The internal listener that handles Queue logic (Looping/Next Track)
    private final OnPlaybackStateListener internalPlaybackListener;

    private MediaPlayer mediaPlayer;
    private boolean loopEnabled = false;
    private boolean mixEnabled = false;
    private List<Track> playQueue = new ArrayList<>();
    private int currentIndex = 0;

    public MusicUtility(Context context, Consumer<String> updateBottomTitle, Runnable updateBottomPlayIcon) {
        this.context = context;
        this.updateBottomPlayIcon = updateBottomPlayIcon;
        this.updateBottomTitle = updateBottomTitle;

        // Define the internal logic
        this.internalPlaybackListener = new OnPlaybackStateListener() {
            @Override
            public void onPlaybackStarted() { }

            @Override
            public void onPlaybackStopped() {
                synchronized (MusicUtility.this) {
                    if (cancelToken.get()) {
                        return;
                    }
                    // Loop Logic: If loop is enabled, don't move to next index.
                    if (!loopEnabled) {
                        currentIndex++;
                    }
                    playNextInQueue();
                }
            }
        };
    }

    /**
     * Public method to play a single track.
     * FIX: Wraps the UI listener so both UI updates AND internal loop logic run.
     */
    public void play(Track track, OnPlaybackStateListener uiListener, long... timespan) {
        cancelToken.set(false);
        this.playQueue = new ArrayList<>(); 
        this.playQueue.add(track);
        this.currentIndex = 0;
        this.mixEnabled = false;

        // Create a Composite Listener
        OnPlaybackStateListener compositeListener = new OnPlaybackStateListener() {
            @Override
            public void onPlaybackStarted() {
                if (uiListener != null) uiListener.onPlaybackStarted();
            }

            @Override
            public void onPlaybackStopped() {
                // 1. Update UI (Main Activity)
                if (uiListener != null) uiListener.onPlaybackStopped();

                // 2. Trigger Internal Logic (Queue/Loop)
                internalPlaybackListener.onPlaybackStopped();
            }
        };

        playInternal(track, compositeListener, timespan);
    }

    /**
     * Internal method handles MediaPlayer.
     */
    private void playInternal(Track track, OnPlaybackStateListener listener, long... timespan) {
        if (isInitialized()) {
            mediaPlayer.release();
        }
        mediaPlayer = new MediaPlayer();

        long startMs;
        long endMs;

        if (timespan != null && timespan.length >= 2) {
            startMs = timespan[0];
            endMs = timespan[1];
        } else {
            startMs = track.startTime;
            endMs = track.endTime;
        }

        try {
            mediaPlayer.setDataSource(context, Uri.fromFile(new File(track.uri)));
            mediaPlayer.setOnPreparedListener(mp -> mp.seekTo((int) startMs));
            mediaPlayer.setOnSeekCompleteListener(mp -> {
                mp.start();
                if (listener != null) {
                    listener.onPlaybackStarted();
                }

                long durationMs = endMs - startMs;
                handler.postDelayed(() -> {
                    if (mediaPlayer == mp && mp.isPlaying()) {
                        mp.pause();
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
            // End of playlist
            if (loopEnabled) {
                currentIndex = 0;
            } else {
                mixEnabled = false;
                updateBottomPlayIcon.run();
                return;
            }
        }

        Track nextTrack = playQueue.get(currentIndex);
        updateBottomTitle.accept(nextTrack.title);

        // Pass the internal listener here so the chain continues
        playInternal(nextTrack, internalPlaybackListener);
    }

    public boolean toggleLoop() {
        loopEnabled = !loopEnabled;
        return loopEnabled;
    }

    public synchronized void stopAndCancel() {
        cancelToken.set(true);
        handler.removeCallbacksAndMessages(null);
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