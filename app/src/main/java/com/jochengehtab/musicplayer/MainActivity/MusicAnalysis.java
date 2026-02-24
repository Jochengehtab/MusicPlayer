package com.jochengehtab.musicplayer.MainActivity;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AlertDialog;

import com.jochengehtab.musicplayer.AudioClassifier.AudioClassifier;
import com.jochengehtab.musicplayer.Data.AppDatabase;
import com.jochengehtab.musicplayer.Data.Track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MusicAnalysis {

    private final AppDatabase database;
    private final List<String> analysisQueueTitles = Collections.synchronizedList(new LinkedList<>());
    private final AtomicInteger pendingTasksCount = new AtomicInteger(0);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int OPTIMAL_THREAD_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors() / 3);
    private final ThreadLocal<AudioClassifier> threadLocalClassifier;
    private final ExecutorService analysisExecutor;
    private AlertDialog analysisDialog;
    private ArrayAdapter<String> queueAdapter;
    private final Map<Long, TaskStatus> activeTasks = new ConcurrentHashMap<>();
    private final AtomicLong totalTimeSpentProcessing = new AtomicLong(0);
    private final AtomicInteger totalTracksProcessed = new AtomicInteger(0);
    private static final long DEFAULT_ESTIMATE_MS = 15000; // 15s default if no data yet
    private final ExecutorService executor;


    // TODO check if passing via constructor is the best option for the database
    public MusicAnalysis(AppDatabase database, ExecutorService executor, AudioClassifier audioClassifier) {
        this.database = database;
        this.executor = executor;
        analysisExecutor = Executors.newFixedThreadPool(OPTIMAL_THREAD_COUNT);
        threadLocalClassifier = ThreadLocal.withInitial(() -> audioClassifier);
    }

    public void checkAndStartAnalysis(MusicAnalysisCallback callback) {
        executor.execute(() -> {
            // Get all unanalyzed tracks
            List<Track> allTracks = database.trackDao().getAllTracks();
            List<Track> unanalyzedTracks = new ArrayList<>();

            for (Track track : allTracks) {
                if (track.embeddingVector == null || track.embeddingVector.isEmpty()) {
                    unanalyzedTracks.add(track);
                }
            }
            if (unanalyzedTracks.isEmpty()) return;
            pendingTasksCount.addAndGet(unanalyzedTracks.size());

            // The analysis has begun
            callback.onStarted();

            // Submit Tasks
            for (Track track : unanalyzedTracks) {
                analysisExecutor.execute(() -> {
                    long threadId = Thread.currentThread().getId();
                    long startTime = System.currentTimeMillis();

                    // 1. Register Task Start
                    activeTasks.put(threadId, new TaskStatus(track.title, startTime));

                    updateDialogStatus(callback);

                    try {
                        Uri uri = Uri.parse(track.uri);
                        AudioClassifier classifier = threadLocalClassifier.get();
                        assert classifier != null;
                        float[] vector = classifier.getStyleEmbedding(uri, (percent, msg) -> {
                            // 2. Update Status
                            TaskStatus status = activeTasks.get(threadId);
                            if (status != null) {
                                status.progress = percent;
                            }
                            updateDialogStatus(callback);
                        });

                        if (vector.length > 0) {
                            // Atomic Update Logic
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < vector.length; i++) {
                                if (i > 0) sb.append(",");
                                sb.append(vector[i]);
                            }
                            database.trackDao().updateTrackEmbedding(track.id, sb.toString());
                        }
                    } catch (Exception e) {
                        Log.e("Analysis", "Error analyzing " + track.title, e);
                    } finally {
                        // Metrics update
                        long duration = System.currentTimeMillis() - startTime;
                        totalTimeSpentProcessing.addAndGet(duration);
                        totalTracksProcessed.incrementAndGet();

                        // 3. Remove Task on Finish
                        activeTasks.remove(threadId);
                    }

                    // Remove from Queue & Cleanup
                    analysisQueueTitles.remove(track.title);
                    int remaining = pendingTasksCount.decrementAndGet();

                    handler.post(() -> {
                        if (queueAdapter != null) queueAdapter.notifyDataSetChanged();
                        // Also update dialog to remove the finished bar
                        updateDialogStatus(callback);

                        if (remaining == 0) {
                            callback.onFinish();
                        }
                    });
                });
            }
        });
    }

    // TODO make here a seperate class that holds the active tasks so that i only need to remove the object instead of rebuilding it
    public void updateDialogStatus(MusicAnalysisCallback callback) {
        if (analysisDialog != null && analysisDialog.isShowing()) {

            // Get all active tasks
            List<TaskStatus> statusList = new ArrayList<>(activeTasks.values());

            // Sort them to prevent jumping
            statusList.sort(Comparator.comparing(s -> s.trackTitle));

            callback.onUpdate(statusList, calculateETA());
        }
    }

    private String calculateETA() {
        int itemsInQueue = analysisQueueTitles.size();

        long avgTimePerTrack = (totalTracksProcessed.get() > 0)
                ? totalTimeSpentProcessing.get() / totalTracksProcessed.get()
                : DEFAULT_ESTIMATE_MS;

        // Queue Time
        long timeForQueue = (itemsInQueue * avgTimePerTrack) / OPTIMAL_THREAD_COUNT;

        // Add Average Remaining time for current active tasks
        // (Simplified: assume active tasks are halfway done on average)
        long timeForActive = (activeTasks.size() * avgTimePerTrack) / 2;

        long totalRemainingMs = timeForActive + timeForQueue;

        long minutes = (totalRemainingMs / 1000) / 60;
        long seconds = (totalRemainingMs / 1000) % 60;

        String timeString = (minutes > 0) ? minutes + "m " + seconds + "s" : seconds + "s";

        return "Queue: " + itemsInQueue + " tracks waiting\n" +
                "Est. time: " + timeString + " (" + OPTIMAL_THREAD_COUNT + " threads)";
    }
}
