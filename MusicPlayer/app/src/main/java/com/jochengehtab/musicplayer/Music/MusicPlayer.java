package com.jochengehtab.musicplayer.Music;

import android.net.Uri;

import com.jochengehtab.musicplayer.MusicList.Track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class MusicPlayer {
    private final MusicUtility musicUtility;
    private final Random random = new Random();

    /**
     * “cancelToken” tells us if the current mix has been canceled.
     * When starting a fresh mix, we flip it to false, and if someone
     * calls playMix(...) again, we set it true, so the old listener
     * knows never to continue.
     */
    private final AtomicBoolean cancelToken = new AtomicBoolean(false);

    /**
     * The queue of Tracks to play, in shuffled order.
     */
    private List<Track> playQueue = new ArrayList<>();

    /**
     * Index of the next track inside playQueue.
     */
    private int currentIndex = 0;

    public MusicPlayer(MusicUtility musicUtility) {
        this.musicUtility = musicUtility;
    }

    /**
     * Plays all the given musicFiles in random order, one after another.
     * If a previous mix is in progress, it’s canceled immediately.
     */
    public synchronized void playMix(List<Track> musicFiles) {
        // Cancel any existing mix in progress
        cancelToken.set(true);

        // Build a fresh cancel token for the new mix
        cancelToken.set(false);

        playQueue = new ArrayList<>(musicFiles);
        Collections.shuffle(playQueue, random);

        currentIndex = 0;

        playNextInQueue();
    }

    /**
     * Called (once) to begin playing playQueue.get(currentIndex).
     * When that track completes, onPlaybackStopped() will call this method again.
     */
    private void playNextInQueue() {
        if (cancelToken.get() || currentIndex >= playQueue.size()) {
            return;
        }

        Uri nextUri = playQueue.get(currentIndex).uri();

        musicUtility.play(nextUri, new OnPlaybackStateListener() {
            @Override
            public void onPlaybackStarted() {
                // we don't need to do anything special when each track starts
            }

            @Override
            public void onPlaybackStopped() {
                // Called when this one URI finishes playing
                synchronized (MusicPlayer.this) {
                    if (cancelToken.get()) {
                        return;
                    }
                    currentIndex++;
                    playNextInQueue();
                }
            }
        });
    }

    /**
     * Call this whenever you want to give up on the current mix entirely.
     * The currently playing track will NOT be forcibly stopped (you could,
     * but that’s optional); it just ensures no further tracks start.
     */
    public synchronized void cancelMix() {
        cancelToken.set(true);
    }

    /**
     * Convenience if you want to free resources immediately:
     * Stops whatever is playing right now and cancels the queue.
     */
    public synchronized void stopAndCancel() {
        cancelToken.set(true);
        musicUtility.stopAndRelease();
    }
}
