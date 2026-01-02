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
import java.util.LinkedList;
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
    private final LinkedList<Long> recentHistory = new LinkedList<>();
    private static final int HISTORY_SIZE = 10; // Remember last 10 songs

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
        smartModeEnabled = false;

        // Clear history on new playlist to give a fresh start
        recentHistory.clear();

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

        addToHistory(track.id);

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

    // Helper to manage history size
    private void addToHistory(long trackId) {
        recentHistory.remove(trackId); // Move to end if played again
        recentHistory.add(trackId);

        // Keep history size limited
        if (recentHistory.size() > HISTORY_SIZE) {
            recentHistory.removeFirst(); // Remove oldest
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

        // Create a copy of the history to pass to the thread safely
        List<Long> historySnapshot = new ArrayList<>(recentHistory);

        executor.execute(() -> {
            List<Track> allTracks = database.trackDao().getAllTracks();

            // PASS HISTORY SNAPSHOT HERE
            Track nextTrack = MusicRecommendationEngine.findNextSong(currentTrack, allTracks, historySnapshot);

            handler.post(() -> {
                if (nextTrack != null) {
                    playQueue.add(nextTrack);
                } else {
                    // Fallback
                    smartModeEnabled = false;
                }
                currentIndex++;
                playCurrentQueueItem();
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

    public boolean isPlaying() { return isInitialized() && mediaPlayer.isPlaying(); }
    public boolean isLooping() { return loopEnabled; }
    public boolean isMixing() { return mixEnabled; }
    public boolean isSmartMode() { return smartModeEnabled; } // Getter for UI
    private boolean isInitialized() { return mediaPlayer != null; }
}