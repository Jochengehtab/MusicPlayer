package com.jochengehtab.musicplayer.Music;

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
    private final Runnable updateBottomPlayIcon;

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
        musicUtility.play(nextTrack.uri(), new OnPlaybackStateListener() {
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
        if (playQueue.isEmpty() || currentIndex >= playQueue.size()) {
            return null;
        }
        return playQueue.get(currentIndex);
    }

    public synchronized void stopAndCancel() {
        cancelToken.set(true);
        musicUtility.stopAndRelease();
        mixEnabled = false;
    }
}