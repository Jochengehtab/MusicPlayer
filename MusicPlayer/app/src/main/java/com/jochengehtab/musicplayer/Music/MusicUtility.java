package com.jochengehtab.musicplayer.Music;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.jochengehtab.musicplayer.data.AppDatabase;
import com.jochengehtab.musicplayer.data.Track;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class MusicUtility {
    private final Context context;
    private final AppDatabase database; // Needed for Smart DJ
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(); // For background DB logic
    private final AtomicBoolean cancelToken = new AtomicBoolean(false);

    private final Consumer<String> updateBottomTitle;
    private final Runnable updateBottomPlayIcon;

    private MediaPlayer mediaPlayer;

    // Modes
    private boolean loopEnabled = false;
    private boolean mixEnabled = false;
    private boolean smartModeEnabled = false; // NEW: Smart DJ Mode

    private List<Track> playQueue = new ArrayList<>();
    private int currentIndex = 0;
    private OnPlaybackStateListener onPlaybackStateListener;

    // To handle Resume logic correctly
    private long currentActiveEndTime = 0;

    public MusicUtility(Context context, AppDatabase database, Consumer<String> updateBottomTitle, Runnable updateBottomPlayIcon) {
        this.context = context;
        this.database = database;
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
        this.onPlaybackStateListener = null;
        setupQueue(musicFiles, shouldMix);
        playCurrentQueueItem();
    }

    private void setupQueue(List<Track> tracks, boolean shouldMix) {
        cancelToken.set(false);
        mixEnabled = shouldMix;
        loopEnabled = false;
        smartModeEnabled = false; // Reset smart mode on new playlist

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
                currentIndex = 0;
            } else {
                stopAndCancel();
                updateBottomPlayIcon.run();
                return;
            }
        }

        Track track = playQueue.get(currentIndex);
        updateBottomTitle.accept(track.title);

        if (isInitialized()) mediaPlayer.release();
        mediaPlayer = new MediaPlayer();

        long startMs = (timespan != null && timespan.length >= 1) ? timespan[0] : track.startTime;
        long endMs = (timespan != null && timespan.length >= 2) ? timespan[1] : track.endTime;
        this.currentActiveEndTime = endMs;
        long durationMs = endMs - startMs;

        try {
            mediaPlayer.setDataSource(context, Uri.fromFile(new File(track.uri)));
            mediaPlayer.setOnPreparedListener(mp -> mp.seekTo((int) startMs));
            mediaPlayer.setOnSeekCompleteListener(mp -> {
                mp.start();
                notifyListener(true);
                scheduleStop(durationMs, mp);
            });
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            notifyListener(false);
        }
    }

    private void scheduleStop(long delayMs, MediaPlayer targetMp) {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            if (mediaPlayer == targetMp && targetMp.isPlaying()) {
                targetMp.pause();
                notifyListener(false);
                onTrackFinished();
            }
        }, delayMs);
    }

    /**
     * LOGIC CENTER: Decides what song plays next.
     */
    private void onTrackFinished() {
        if (cancelToken.get()) return;

        if (loopEnabled) {
            // Replay same song
            playCurrentQueueItem();
        }
        else if (smartModeEnabled) {
            // --- SMART DJ LOGIC ---
            findAndPlayNextSmartSong();
        }
        else {
            // Standard Next
            currentIndex++;
            playCurrentQueueItem();
        }
    }

    /**
     * Looks at the current song, queries DB for the most similar one, and plays it.
     */
    private void findAndPlayNextSmartSong() {
        if (playQueue.isEmpty()) return;

        Track currentTrack = playQueue.get(currentIndex);

        // Run heavy vector math in background
        executor.execute(() -> {
            List<Track> allTracks = database.trackDao().getAllTracks();
            Track nextTrack = MusicRecommendationEngine.findNextSong(currentTrack, allTracks);

            handler.post(() -> {
                if (nextTrack != null) {
                    // Add found song to the queue and play it
                    playQueue.add(nextTrack);
                    currentIndex++;
                    playCurrentQueueItem();
                } else {
                    Toast.makeText(context, "Smart DJ couldn't find a similar song (Are songs analyzed?)", Toast.LENGTH_LONG).show();
                    // Fallback to normal behavior
                    smartModeEnabled = false;
                    currentIndex++;
                    playCurrentQueueItem();
                }
            });
        });
    }

    private void notifyListener(boolean isStarted) {
        if (onPlaybackStateListener != null) {
            if (isStarted) onPlaybackStateListener.onPlaybackStarted();
            else onPlaybackStateListener.onPlaybackStopped();
        }
    }

    // --- CONTROLS ---

    public boolean toggleSmartMode() {
        smartModeEnabled = !smartModeEnabled;
        if (smartModeEnabled) {
            loopEnabled = false;
            mixEnabled = false;
        }
        return smartModeEnabled;
    }

    public boolean toggleLoop() {
        loopEnabled = !loopEnabled;
        if (loopEnabled) smartModeEnabled = false;
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
        if (isInitialized() && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            handler.removeCallbacksAndMessages(null);
            notifyListener(false);
        }
    }

    public void resume() {
        if (isInitialized() && !mediaPlayer.isPlaying()) {
            int currentPos = mediaPlayer.getCurrentPosition();
            long remainingTime = currentActiveEndTime - currentPos;

            if (remainingTime <= 0) {
                onTrackFinished();
                return;
            }

            mediaPlayer.start();
            notifyListener(true);
            scheduleStop(remainingTime, mediaPlayer);
        } else if (!isInitialized() && !playQueue.isEmpty()) {
            // If resume is called but player died, restart track
            playCurrentQueueItem();
        }
    }

    public synchronized Track getCurrentTrack() {
        if (playQueue.isEmpty() || currentIndex >= playQueue.size()) return null;
        return playQueue.get(currentIndex);
    }

    public boolean isPlaying() { return isInitialized() && mediaPlayer.isPlaying(); }
    public boolean isLooping() { return loopEnabled; }
    public boolean isMixing() { return mixEnabled; }
    public boolean isSmartMode() { return smartModeEnabled; } // Getter for UI
    private boolean isInitialized() { return mediaPlayer != null; }
}