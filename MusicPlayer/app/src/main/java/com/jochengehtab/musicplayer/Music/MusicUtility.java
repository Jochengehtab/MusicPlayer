package com.jochengehtab.musicplayer.Music;

import android.content.Context;
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
    private static final int HISTORY_SIZE = 10; // Remember last 10 songs
    private final Context context;
    private final AppDatabase database;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelToken = new AtomicBoolean(false);
    private final Consumer<String> updateBottomTitle;
    private final Consumer<Boolean> updateBottomPlayIcon;
    private final LinkedList<Long> recentHistory = new LinkedList<>();
    private MediaPlayer mediaPlayer;
    private boolean loopEnabled = false;
    private boolean mixEnabled = false;
    private List<Track> playQueue = new ArrayList<>();
    private int currentIndex = 0;

    public MusicUtility(Context context, AppDatabase database, Consumer<String> updateBottomTitle, Consumer<Boolean> updateBottomPlayIcon) {
        this.context = context;
        this.database = database;
        this.updateBottomTitle = updateBottomTitle;
        this.updateBottomPlayIcon = updateBottomPlayIcon;
    }

    public void playTrack(Track track, long... timespan) {
        if (isInitialized()) mediaPlayer.release();
        mediaPlayer = new MediaPlayer();

        long startMs = (timespan != null && timespan.length >= 1) ? timespan[0] : track.startTime;
        long endMs = (timespan != null && timespan.length >= 2) ? timespan[1] : track.endTime;

        mediaPlayer.setStartTime(startMs);
        mediaPlayer.setEndTime(endMs);
        mediaPlayer.setCurrentTrack(track);

        long durationMs = endMs - startMs;

        try {
            mediaPlayer.setDataSource(context, Uri.fromFile(new File(track.uri)));
            mediaPlayer.setOnPreparedListener(mp -> mp.seekTo((int) startMs));
            mediaPlayer.setOnSeekCompleteListener(mp -> {
                mp.start();
                scheduleStop(durationMs, (MediaPlayer) mp);
            });
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        updateBottomPlayIcon.accept(true);
    }

    public synchronized void playList(List<Track> musicFiles, boolean shouldMix) {
        if (musicFiles == null || musicFiles.isEmpty()) {
            Toast.makeText(context, "Playlist is empty.", Toast.LENGTH_SHORT).show();
            return;
        }
        setupQueue(musicFiles, shouldMix);
        playCurrentQueueItem();
    }

    private synchronized void playCurrentQueueItem(long... timespan) {
        if (cancelToken.get()) return;

        // Boundary Check
        if (currentIndex >= playQueue.size()) {
            if (loopEnabled && !playQueue.isEmpty()) {
                currentIndex = 0;
            } else {
                stopAndCancel();
                updateBottomPlayIcon.accept(isPlaying());
                return;
            }
        }

        Track track = playQueue.get(currentIndex);
        updateBottomTitle.accept(track.title);

        addToHistory(track.id);
        playTrack(track, timespan);
    }

    public void handleLooping() {
        loopEnabled = !loopEnabled;
    }

    public void handleMix(String playListName) {

        // First check if we have a track currently playing
        if (isPlaying()) {
            findAndPlayNextSong();
        } else {
            Collections.shuffle(playQueue);
            playCurrentQueueItem();
        }
    }

    private void setupQueue(List<Track> tracks, boolean shouldMix) {
        cancelToken.set(false);
        mixEnabled = shouldMix;
        loopEnabled = false;

        // Clear history on new playlist to give a fresh start
        recentHistory.clear();

        playQueue = new ArrayList<>(tracks);
        if (shouldMix) {
            Collections.shuffle(playQueue);
        }
        currentIndex = 0;
    }

    private void scheduleStop(long delayMs, MediaPlayer targetMp) {
        // Clear the handler
        handler.removeCallbacksAndMessages(null);

        // Stop the music exactly after delayMs is passed
        handler.postDelayed(() -> {
            if (mediaPlayer == targetMp && targetMp.isPlaying()) {
                targetMp.pause();
                updateBottomPlayIcon.accept(false);
                if (loopEnabled) {
                    // Replay same song
                    playCurrentQueueItem();
                } else {
                    findAndPlayNextSong();
                }
            }
        }, delayMs);
    }

    /**
     * Looks at the current song, queries DB for the most similar one, and plays it.
     */
    private void findAndPlayNextSong() {
        if (playQueue.isEmpty()) return;

        Track currentTrack = playQueue.get(currentIndex);

        // Create a copy of the history to pass to the thread safely
        List<Long> historySnapshot = new ArrayList<>(recentHistory);

        executor.execute(() -> {
            List<Track> allTracks = database.trackDao().getAllTracks();

            Track nextTrack = MusicRecommendationEngine.findNextSong(currentTrack, allTracks, historySnapshot);

            handler.post(() -> {
                if (nextTrack != null) {
                    playQueue.add(nextTrack);
                }
                currentIndex++;
                playCurrentQueueItem();
            });
        });
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
    }

    public void pause() {
        if (!isInitialized()) return;
        mediaPlayer.pause();
        mediaPlayer.setStartTime(mediaPlayer.getCurrentPosition());
        handler.removeCallbacksAndMessages(null);
        updateBottomPlayIcon.accept(false);
    }

    public void resume() {
        if (!isInitialized()) return;
        playTrack(mediaPlayer.getCurrentTrack(), mediaPlayer.getStartTime(), mediaPlayer.getEndTime());

        updateBottomPlayIcon.accept(true);
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