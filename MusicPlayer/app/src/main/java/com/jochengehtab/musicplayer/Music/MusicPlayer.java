package com.jochengehtab.musicplayer.Music;

import android.util.Log;

import com.jochengehtab.musicplayer.MainActivity.MainActivity;
import com.jochengehtab.musicplayer.MusicList.Track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class MusicPlayer {
    private final MusicUtility musicUtility;
    private final Random random = new Random();

    private boolean loopEnabled = false;
    private boolean mixEnabled = false;

    private final AtomicBoolean cancelToken = new AtomicBoolean(false);
    private List<Track> playQueue = new ArrayList<>();
    private int currentIndex = 0;
    private final Consumer<String> updateBottomTitle;
    private final Consumer<Boolean> updateBottomPlay;

    public MusicPlayer(MusicUtility musicUtility, Consumer<String> updateBottomTitle, Consumer<Boolean> updateBottomPlay) {
        this.musicUtility = musicUtility;
        this.updateBottomPlay = updateBottomPlay;
        this.updateBottomTitle = updateBottomTitle;
    }

    public boolean toggleLoop() {
        loopEnabled = !loopEnabled;
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

    private synchronized void playNextInQueue() {
        if (cancelToken.get()) {
            MainActivity.isMixPlaying = false;
            updateBottomPlay.accept(true);
            return;
        }
        if (currentIndex >= playQueue.size()) {
            mixEnabled = false;
            MainActivity.isMixPlaying = false;
            updateBottomPlay.accept(true);
            if (!loopEnabled) {
                return;
            }
            currentIndex = 0;
        }

        Track nextUri = playQueue.get(currentIndex);
        updateBottomTitle.accept(nextUri.title());
        musicUtility.play(nextUri.uri(), new OnPlaybackStateListener() {
            @Override
            public void onPlaybackStarted() {}

            @Override
            public void onPlaybackStopped() {
                // We lock MusicPlayer.this to prevent race condition
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
    }

    public synchronized Track getCurrentTitle() {
        return playQueue.get(currentIndex);
    }

    public synchronized void stopAndCancel() {
        cancelToken.set(true);
        musicUtility.stopAndRelease();
        mixEnabled = false;
    }
}

