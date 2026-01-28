package com.jochengehtab.musicplayer.MainActivity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jochengehtab.musicplayer.AudioClassifier.AudioClassifier;
import com.jochengehtab.musicplayer.Dialog.PlaylistDialog;
import com.jochengehtab.musicplayer.Music.MusicUtility;
import com.jochengehtab.musicplayer.MusicList.OnItemClickListener;
import com.jochengehtab.musicplayer.MusicList.TrackAdapter;
import com.jochengehtab.musicplayer.R;
import com.jochengehtab.musicplayer.Utility.SortingOrder;
import com.jochengehtab.musicplayer.Data.AppDatabase;
import com.jochengehtab.musicplayer.Data.Playlist;
import com.jochengehtab.musicplayer.Data.PlaylistTrackCrossRef;
import com.jochengehtab.musicplayer.Data.PlaylistWithTracks;
import com.jochengehtab.musicplayer.Data.Track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    private static class TaskStatus {
        String trackTitle;
        int progress;
        long startTime;

        TaskStatus(String title, long start) {
            this.trackTitle = title;
            this.startTime = start;
            this.progress = 0;
        }
    }
    public static final String ALL_TRACKS_PLAYLIST_NAME = "All Tracks";
    private static final String PREFS_NAME = "MusicPlayerPrefs";
    private static final String KEY_LAST_PLAYLIST = "last_playlist";
    private static final int PERMISSION_REQUEST_CODE = 101;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final int OPTIMAL_THREAD_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors() / 3);
    private ExecutorService analysisExecutor;
    private final List<String> analysisQueueTitles = Collections.synchronizedList(new LinkedList<>());
    private final AtomicInteger pendingTasksCount = new AtomicInteger(0);

    // Metrics for Total ETA calculation
    private final AtomicLong totalTimeSpentProcessing = new AtomicLong(0);
    private final AtomicInteger totalTracksProcessed = new AtomicInteger(0);
    private static final long DEFAULT_ESTIMATE_MS = 15000; // 15s default if no data yet
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BecomingNoisyReceiver noisyReceiver = new BecomingNoisyReceiver();
    private final IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    private final ThreadLocal<AudioClassifier> threadLocalClassifier = ThreadLocal.withInitial(() -> {
        // Use Application Context to prevent memory leaks if threads outlive the activity
        return new AudioClassifier(getApplicationContext());
    });
    private MusicUtility musicUtility;
    private TrackAdapter trackAdapter;
    private TextView bottomTitle;
    private ImageButton bottomPlay;
    private SearchView searchView;
    private List<Track> currentlyDisplayedTracks = new ArrayList<>();
    private PlaylistDialog playlistDialog;
    private BottomOptions bottomOptions;
    private SortingOrder currentSortOrder = SortingOrder.MOST_RECENT;
    private String currentPlaylistName = ALL_TRACKS_PLAYLIST_NAME;
    private ProgressBar updateProgressBar;
    private AppDatabase database;
    private ImageButton syncStatusButton;
    private Animation rotateAnimation;
    private AlertDialog analysisDialog;
    private ArrayAdapter<String> queueAdapter;
    private TextView dialogEtaText;
    private final Map<Long, TaskStatus> activeTasks = new ConcurrentHashMap<>();
    private LinearLayout activeThreadsContainer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Objects.requireNonNull(getSupportActionBar()).hide();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        setContentView(R.layout.activity_main);

        database = AppDatabase.getDatabase(this);
        musicUtility = new MusicUtility(this, database, this::updateBottomTitle, this::updatePlayButtonIcon);
        analysisExecutor = Executors.newFixedThreadPool(OPTIMAL_THREAD_COUNT);

        RecyclerView musicList = findViewById(R.id.musicList);
        bottomPlay = findViewById(R.id.bottom_play);
        bottomTitle = findViewById(R.id.bottom_title);
        updateProgressBar = findViewById(R.id.update_progress_bar);
        searchView = findViewById(R.id.track_search_view);
        syncStatusButton = findViewById(R.id.sync_status_button);
        rotateAnimation = AnimationUtils.loadAnimation(this, R.anim.rotate_infinite);
        syncStatusButton.setOnClickListener(v -> showAnalysisStatusDialog());

        // The track is just the parameter of the function 'onItemClick'
        OnItemClickListener itemClickListener = track -> {
            bottomTitle.setText(track.title);
            musicUtility.playTrack(track);
        };

        trackAdapter = new TrackAdapter(
                this,
                itemClickListener,
                musicUtility,
                database
        );

        syncStatusButton.setOnClickListener(v -> showAnalysisStatusDialog());

        musicList.setLayoutManager(new LinearLayoutManager(this));
        musicList.setAdapter(trackAdapter);

        // Read the last played playlist
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentPlaylistName = prefs.getString(KEY_LAST_PLAYLIST, ALL_TRACKS_PLAYLIST_NAME);

        if (hasPermissions()) {
            scanAndLoadMusic();
        } else {
            requestPermissions();
        }

        setupUI();
        registerReceiver(noisyReceiver, intentFilter);
    }

    private void checkAndStartAnalysis() {
        executor.execute(() -> {
            List<Track> allTracks = database.trackDao().getAllTracks();
            List<Track> unanalyzedTracks = new ArrayList<>();

            for (Track t : allTracks) {
                if (t.embeddingVector == null || t.embeddingVector.isEmpty()) {
                    unanalyzedTracks.add(t);
                }
            }

            if (unanalyzedTracks.isEmpty()) return;

            // Sync UI Queue
            for (Track t : unanalyzedTracks) {
                if (!analysisQueueTitles.contains(t.title)) {
                    analysisQueueTitles.add(t.title);
                }
            }

            pendingTasksCount.addAndGet(unanalyzedTracks.size());

            // Start Animation
            handler.post(() -> {
                if (syncStatusButton.getVisibility() != View.VISIBLE) {
                    syncStatusButton.setVisibility(View.VISIBLE);
                    syncStatusButton.startAnimation(rotateAnimation);
                }
                if (queueAdapter != null) queueAdapter.notifyDataSetChanged();
            });

            // Submit Tasks
            for (Track track : unanalyzedTracks) {
                analysisExecutor.execute(() -> {
                    long threadId = Thread.currentThread().getId();
                    long startTime = System.currentTimeMillis();

                    // 1. Register Task Start
                    activeTasks.put(threadId, new TaskStatus(track.title, startTime));

                    handler.post(this::updateDialogStatus);

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
                            // Throttled UI update
                            handler.post(this::updateDialogStatus);
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
                        updateDialogStatus();

                        if (remaining == 0) {
                            syncStatusButton.clearAnimation();
                            syncStatusButton.setVisibility(View.GONE);
                        }
                    });
                });
            }
        });
    }

    /**
     * Rebuilds the "Active Threads" list in the dialog.
     */
    private void updateDialogStatus() {
        if (analysisDialog != null && analysisDialog.isShowing() && activeThreadsContainer != null) {

            // 1. Get all active tasks
            List<TaskStatus> statusList = new ArrayList<>(activeTasks.values());

            // 2. Sort them (e.g., by Title alphabetically or Start Time) to prevent jumping
            statusList.sort(Comparator.comparing(s -> s.trackTitle));

            // 3. Limit to 3 (User Constraint)
            int limit = Math.min(statusList.size(), 3);

            // 4. Update UI
            activeThreadsContainer.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(this);

            for (int i = 0; i < limit; i++) {
                TaskStatus task = statusList.get(i);
                View row = inflater.inflate(R.layout.item_analysis_thread, activeThreadsContainer, false);

                TextView title = row.findViewById(R.id.thread_track_title);
                ProgressBar bar = row.findViewById(R.id.thread_progress_bar);

                title.setText(task.trackTitle);
                bar.setProgress(task.progress);

                activeThreadsContainer.addView(row);
            }

            // 5. Update ETA
            if (dialogEtaText.getVisibility() == View.VISIBLE) {
                updateTotalEtaCalculation();
            }
        }
    }

    private void updateTotalEtaCalculation() {
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

        String infoText = "Queue: " + itemsInQueue + " tracks waiting\n" +
                "Est. time: " + timeString + " (" + OPTIMAL_THREAD_COUNT + " threads)";

        dialogEtaText.setText(infoText);
    }

    private void showAnalysisStatusDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_analysis_status, null);
        builder.setView(dialogView);

        // Bind Views
        activeThreadsContainer = dialogView.findViewById(R.id.active_threads_container);
        dialogEtaText = dialogView.findViewById(R.id.status_eta_text);
        ImageView infoIcon = dialogView.findViewById(R.id.status_info_icon);
        ListView queueListView = dialogView.findViewById(R.id.status_queue_list);

        // Initial Data Populate
        updateDialogStatus();

        queueAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, analysisQueueTitles);
        queueListView.setAdapter(queueAdapter);

        infoIcon.setOnClickListener(v -> {
            if (dialogEtaText.getVisibility() == View.VISIBLE) {
                dialogEtaText.setVisibility(View.GONE);
            } else {
                dialogEtaText.setVisibility(View.VISIBLE);
                updateTotalEtaCalculation();
            }
        });

        builder.setTitle("Analysis Status")
                .setPositiveButton("Close", null);

        analysisDialog = builder.create();
        analysisDialog.show();

        analysisDialog.setOnDismissListener(d -> {
            analysisDialog = null;
            activeThreadsContainer = null;
            queueAdapter = null;
        });
    }

    private void handlePlayPauseClick() {
        if (musicUtility.isPlaying()) {
            musicUtility.pause();
        } else {
            musicUtility.resume();
        }
    }

    private void setupUI() {
        bottomOptions = new BottomOptions(this, musicUtility);
        playlistDialog = new PlaylistDialog(this, database, this::loadAndShowPlaylist);
        bottomPlay.setOnClickListener(v -> handlePlayPauseClick());

        ImageButton bottomOptionsButton = findViewById(R.id.bottom_options);
        bottomOptions.handleBottomOptions(bottomOptionsButton);

        final Animation slideDown = AnimationUtils.loadAnimation(this, R.anim.slide_down_fade_in);
        final Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_out);

        slideUp.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                searchView.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        // Attach listeners to the UI buttons
        ImageButton burgerMenu = findViewById(R.id.burger_menu);
        burgerMenu.setOnClickListener(v -> playlistDialog.showPlaylistDialog());

        ImageButton searchIcon = findViewById(R.id.search_icon);
        searchIcon.setOnClickListener(v -> {
            if (searchView.getVisibility() == View.GONE) {
                searchView.setVisibility(View.VISIBLE);
                searchView.startAnimation(slideDown);
                searchView.requestFocus();
            } else {
                searchView.setQuery("", false);
                searchView.startAnimation(slideUp);
            }
        });

        findViewById(R.id.sort_button).setOnClickListener(this::showSortMenu);

        searchView.setOnCloseListener(() -> {
            searchView.setQuery("", false);
            searchView.startAnimation(slideUp);
            return true;
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterTracks(newText);
                return true;
            }
        });
    }

    private void scanAndLoadMusic() {
        updateProgressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            // Get a fresh list of tracks from MediaStore
            List<Track> mediaStoreTracks = new ArrayList<>();
            String[] projection = {
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATE_MODIFIED
            };
            String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
            try (Cursor cursor = getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection, selection, null, null)) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        mediaStoreTracks.add(new Track(
                                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)),
                                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
                                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED))
                        ));
                    }
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Error scanning MediaStore", e);
            }

            database.trackDao().syncTracks(mediaStoreTracks);

            // Collect all IDs found currently on the phone
            List<String> currentMediaStoreUris = mediaStoreTracks.stream()
                    .map(track -> track.uri)
                    .collect(Collectors.toList());

            if (!currentMediaStoreUris.isEmpty()) {
                database.trackDao().deleteOrphanedTracks(currentMediaStoreUris);
            }

            List<Track> allTracksFromDb = database.trackDao().getAllTracks();

            Playlist allTracksPlaylist = database.playlistDao().getPlaylist(ALL_TRACKS_PLAYLIST_NAME);
            long playlistId;

            if (allTracksPlaylist == null) {
                playlistId = database.playlistDao().createPlaylist(new Playlist(ALL_TRACKS_PLAYLIST_NAME));
            } else {
                playlistId = allTracksPlaylist.id;
                database.playlistDao().removeAllTracksFromPlaylist(playlistId);
            }

            // 4. Create Links
            List<PlaylistTrackCrossRef> crossRefs = new ArrayList<>();
            for (Track track : allTracksFromDb) {
                PlaylistTrackCrossRef ref = new PlaylistTrackCrossRef();
                ref.playlistId = playlistId;
                ref.trackId = track.id;
                crossRefs.add(ref);
            }

            if (!crossRefs.isEmpty()) {
                database.playlistDao().addTracksToPlaylist(crossRefs);
            }

            // 5. Update UI
            handler.post(() -> {
                updateProgressBar.setVisibility(View.GONE);
                loadAndShowPlaylist(currentPlaylistName);
                checkAndStartAnalysis();
            });
        });
    }

    // TODO save selected sorting scheme
    private void showSortMenu(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.getMenuInflater().inflate(R.menu.sort_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.sort_a_z) {
                currentSortOrder = SortingOrder.A_TO_Z;
            } else if (itemId == R.id.sort_date) {
                currentSortOrder = SortingOrder.MOST_RECENT;
            }
            loadAndShowPlaylist(currentPlaylistName);
            return true;
        });
        popup.show();
    }

    private void filterTracks(String query) {
        if (query.isEmpty()) {
            trackAdapter.updateList(currentlyDisplayedTracks);
            return;
        }
        List<Track> filteredTracks = currentlyDisplayedTracks.stream()
                .filter(track -> track.title.toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
        trackAdapter.updateList(filteredTracks);
    }

    /**
     * Loads a playlist's tracks from the database.
     */
    private void loadAndShowPlaylist(String playlistName) {
        musicUtility.stopAndCancel();
        currentPlaylistName = playlistName;
        bottomOptions.setPlaylistName(playlistName);
        trackAdapter.setCurrentPlaylistName(playlistName);

        // Save the current playlist as the last Playlist
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_LAST_PLAYLIST, playlistName).apply();

        executor.execute(() -> {
            PlaylistWithTracks pwt = database.playlistDao().getPlaylistWithTracks(playlistName);
            List<Track> playlistTracks = (pwt != null) ? pwt.tracks : new ArrayList<>();

            // Apply sorting
            if (currentSortOrder == SortingOrder.A_TO_Z) {
                playlistTracks.sort((t1, t2) -> t1.title.compareToIgnoreCase(t2.title));
            } else { // MOST_RECENT
                playlistTracks.sort((t1, t2) -> Long.compare(t2.dateModified, t1.dateModified));
            }

            currentlyDisplayedTracks = new ArrayList<>(playlistTracks);

            handler.post(() -> {
                trackAdapter.updateList(currentlyDisplayedTracks);
                bottomTitle.setText(R.string.no_track_selected);
                updatePlayButtonIcon();
            });
        });
    }

    public void updatePlayButtonIcon() {
        updatePlayButtonIcon(musicUtility.isPlaying());
    }

    public void updatePlayButtonIcon(boolean setStopIcon) {
        if (setStopIcon) {
            bottomPlay.setImageResource(R.drawable.ic_stop_white_24dp);
        } else {
            bottomPlay.setImageResource(R.drawable.ic_play_arrow_white_24dp);
        }
    }

    public void updateBottomTitle(String newTitle) {
        bottomTitle.setText(newTitle);
    }

    private boolean hasPermissions() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        ActivityCompat.requestPermissions(this, new String[]{permission}, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanAndLoadMusic();
            } else {
                Toast.makeText(this, "Permission denied. Cannot load music.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        musicUtility.destroy();
        unregisterReceiver(noisyReceiver);
        executor.shutdown();
        if (analysisExecutor != null) {
            analysisExecutor.shutdownNow();
        }
    }

    private class BecomingNoisyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                if (musicUtility.isPlaying()) {
                    musicUtility.pause();
                    updatePlayButtonIcon();
                }
            }
        }
    }
}