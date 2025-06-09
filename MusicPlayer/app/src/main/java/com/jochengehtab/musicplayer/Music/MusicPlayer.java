package com.jochengehtab.musicplayer.Music;

import com.jochengehtab.musicplayer.MusicList.Track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class MusicPlayer {
    private final MusicUtility musicUtility;
    private final Random random = new Random();

    private boolean loopEnabled = false;
    private boolean mixEnabled = false;

    private final AtomicBoolean cancelToken = new AtomicBoolean(false);
    private List<Track> playQueue = new ArrayList<>();
    private int currentIndex = 0;

    public MusicPlayer(MusicUtility musicUtility) {
        this.musicUtility = musicUtility;
    }

    public boolean toggleLoop() {
        loopEnabled = !loopEnabled;
        if (loopEnabled) {
            mixEnabled = false; // Ensure only one is active
        }
        return loopEnabled;
    }

    public synchronized void playMix(List<Track> musicFiles) {
        cancelToken.set(false);

        mixEnabled = true;
        loopEnabled = false;

        playQueue = new ArrayList<>(musicFiles);
        Collections.shuffle(playQueue, random);
        currentIndex = 0;

        playNextInQueue();
    }

    private void playNextInQueue() {
        if (cancelToken.get()) {
            return;
        }
        if (currentIndex >= playQueue.size()) {
            if (loopEnabled) {
                currentIndex = 0;
            } else {
                return;
            }
        }

        Track nextUri = playQueue.get(currentIndex);
        musicUtility.play(nextUri.uri(), new OnPlaybackStateListener() {
            @Override
            public void onPlaybackStarted() {}

            @Override
            public void onPlaybackStopped() {
                synchronized (MusicPlayer.this) {
                    if (cancelToken.get()) return;
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
    }

    public synchronized void stopAndCancel() {
        cancelToken.set(true);
        musicUtility.stopAndRelease();
        mixEnabled = false;
    }
}

