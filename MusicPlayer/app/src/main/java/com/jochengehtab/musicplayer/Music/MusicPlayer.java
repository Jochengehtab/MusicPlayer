package com.jochengehtab.musicplayer.Music;

import com.jochengehtab.musicplayer.MainActivity.MainActivity;
import com.jochengehtab.musicplayer.MusicList.Track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class MusicPlayer {
    private final MusicUtility musicUtility;
    private final Random random = new Random();
    private final AtomicBoolean cancelToken = new AtomicBoolean(false);
    private final Consumer<String> updateBottomTitle;
    private final Runnable updateBottomPlayIcon;
    private boolean loopEnabled = false;
    private boolean mixEnabled = false;
    private List<Track> playQueue = new ArrayList<>();
    private int currentIndex = 0;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public MusicPlayer(MusicUtility musicUtility, Consumer<String> updateBottomTitle, Runnable updateBottomPlayIcon) {
        this.musicUtility = musicUtility;
        this.updateBottomPlayIcon = updateBottomPlayIcon;
        this.updateBottomTitle = updateBottomTitle;
    }

    public boolean toggleLoop() {
        loopEnabled = !loopEnabled;
        return loopEnabled;
    }

    public synchronized void playMix(List<Track> musicFiles) {
        // Submit the heavy work to the background thread
        executor.execute(() -> {
            cancelToken.set(false);
            mixEnabled = true;
            MainActivity.isMixPlaying = true;

            // Loop mixes by default
            loopEnabled = true;

            playQueue = new ArrayList<>(musicFiles);
            Collections.shuffle(playQueue, random);
            currentIndex = 0;

            // Start playing the first track
            playNextInQueue();
        });
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

        // Submit the heavy work to the background thread
        executor.execute(() -> {
            cancelToken.set(false);

            // Treat as a "mix" for playback control
            mixEnabled = true;
            MainActivity.isMixPlaying = true;

            // Loop playlists by default
            loopEnabled = true;

            playQueue = new ArrayList<>(musicFiles);
            currentIndex = 0;

            // Start playing the first track
            playNextInQueue();
        });
    }


    private synchronized void playNextInQueue() {
        if (cancelToken.get()) {
            MainActivity.isMixPlaying = false;
            updateBottomPlayIcon.run();
            return;
        }

        if (currentIndex >= playQueue.size()) {
            // If we've reached the end of the queue
            if (!loopEnabled) {
                // If looping is off, stop everything.
                mixEnabled = false;
                MainActivity.isMixPlaying = false;
                updateBottomPlayIcon.run();
                return;
            }
            // If looping is on, just reset to the beginning.
            currentIndex = 0;
        }

        // Check again if the queue is empty after a potential loop reset
        if (playQueue.isEmpty()) {
            mixEnabled = false;
            MainActivity.isMixPlaying = false;
            updateBottomPlayIcon.run();
            return;
        }

        Track nextTrack = playQueue.get(currentIndex);
        updateBottomTitle.accept(nextTrack.title());
        musicUtility.play(nextTrack.uri(), new OnPlaybackStateListener() {
            @Override
            public void onPlaybackStarted() {
                // The icon should be updated as soon as playback starts
                updateBottomPlayIcon.run();
            }

            @Override
            public void onPlaybackStopped() {
                // We lock to prevent race conditions when a song ends and the user
                // simultaneously clicks something else.
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


    public boolean isLooping() {
        return loopEnabled;
    }

    public boolean isMixing() {
        return mixEnabled;
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
        musicUtility.stopAndRelease();
        mixEnabled = false;
        MainActivity.isMixPlaying = false;
    }

    public void shutdown() {
        executor.shutdownNow(); // Stop any pending tasks
        stopAndCancel();
    }
}