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
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
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
import com.jochengehtab.musicplayer.Music.OnPlaybackStateListener;
import com.jochengehtab.musicplayer.MusicList.TrackAdapter;
import com.jochengehtab.musicplayer.R;
import com.jochengehtab.musicplayer.Utility.SortingOrder;
import com.jochengehtab.musicplayer.data.AppDatabase;
import com.jochengehtab.musicplayer.data.Playlist;
import com.jochengehtab.musicplayer.data.PlaylistTrackCrossRef;
import com.jochengehtab.musicplayer.data.PlaylistWithTracks;
import com.jochengehtab.musicplayer.data.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    public static final String ALL_TRACKS_PLAYLIST_NAME = "All Tracks";
    private static final String PREFS_NAME = "MusicPlayerPrefs";
    private static final String KEY_LAST_PLAYLIST = "last_playlist";
    private static final int PERMISSION_REQUEST_CODE = 101;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BecomingNoisyReceiver noisyReceiver = new BecomingNoisyReceiver();
    private final IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    private MusicUtility musicUtility;
    private TrackAdapter trackAdapter;
    private TextView bottomTitle;
    private ImageButton bottomPlay;
    private SearchView searchView;
    private List<Track> currentlyDisplayedTracks = new ArrayList<>();
    private PlaylistDialog playlistDialog;
    private BottomOptions bottomOptions;
    private SortingOrder currentSortOrder = SortingOrder.A_TO_Z;
    private String currentPlaylistName = ALL_TRACKS_PLAYLIST_NAME;
    private ProgressBar updateProgressBar;
    private AppDatabase database;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Objects.requireNonNull(getSupportActionBar()).hide();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        setContentView(R.layout.activity_main);

        database = AppDatabase.getDatabase(this);
        musicUtility = new MusicUtility(this, database, this::updateBottomTitle, this::updatePlayButtonIcon);

        RecyclerView musicList = findViewById(R.id.musicList);
        bottomPlay = findViewById(R.id.bottom_play);
        bottomTitle = findViewById(R.id.bottom_title);
        updateProgressBar = findViewById(R.id.update_progress_bar);
        searchView = findViewById(R.id.track_search_view);

        OnPlaybackStateListener playbackListener = new OnPlaybackStateListener() {
            @Override
            public void onPlaybackStarted() {
                runOnUiThread(MainActivity.this::updatePlayButtonIcon);
            }

            @Override
            public void onPlaybackStopped() {
                runOnUiThread(MainActivity.this::updatePlayButtonIcon);
            }
        };

        trackAdapter = new TrackAdapter(
                this,
                new ArrayList<>(),
                track -> {
                    musicUtility.stopAndCancel();
                    bottomTitle.setText(track.title);
                    bottomPlay.setImageResource(R.drawable.ic_stop_white_24dp);
                    musicUtility.play(track, playbackListener);
                },
                musicUtility,
                database
        );

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

    private void analyzeAllTracks() {
        // 1. Setup the Custom Dialog
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_library_scan, null);
        TextView tvMessage = dialogView.findViewById(R.id.dialog_message);
        TextView tvCount = dialogView.findViewById(R.id.dialog_count);
        ProgressBar progressBar = dialogView.findViewById(R.id.dialog_progress_bar);

        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false) // Prevent dismissal during scan
                .create();

        progressDialog.show();

        // 2. Create Executor
        ExecutorService scanningExecutor = Executors.newSingleThreadExecutor();

        scanningExecutor.execute(() -> {
            try {
                List<Track> allTracks = database.trackDao().getAllTracks();

                // Initialize Classifier (Heavy object, create once)
                AudioClassifier classifier = new AudioClassifier(this);

                int total = allTracks.size();
                int current = 0;
                int successCount = 0;

                // Update Max Progress on UI
                runOnUiThread(() -> progressBar.setMax(total));

                for (Track track : allTracks) {
                    current++;

                    // Check for cancellation or issues
                    if (Thread.currentThread().isInterrupted()) break;

                    // UI Updates (Final variables for lambda)
                    int finalCurrent = current;
                    runOnUiThread(() -> {
                        progressBar.setProgress(finalCurrent);
                        tvMessage.setText("Analyzing: " + track.title);
                        tvCount.setText(finalCurrent + " / " + total);
                    });

                    // Skip if already analyzed
                    if (track.embeddingVector != null && !track.embeddingVector.isEmpty()) {
                        continue;
                    }

                    // Perform Analysis
                    float[] vector = classifier.getStyleEmbedding(Uri.parse(track.uri));

                    if (vector.length > 0) {
                        // Convert float[] to String
                        // Using StringBuilder for maximum compatibility/performance in tight loops
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < vector.length; i++) {
                            if (i > 0) sb.append(",");
                            sb.append(vector[i]);
                        }

                        track.embeddingVector = sb.toString();
                        database.trackDao().updateTrack(track);
                        successCount++;
                    }
                }

                // Completion UI
                int finalSuccessCount = successCount;
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Analysis Complete! " + finalSuccessCount + " new tracks analyzed.", Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Error during analysis: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
                throw new RuntimeException(e);
            } finally {
                // 3. Clean up Executor
                scanningExecutor.shutdown();
            }
        });
    }

    private void setupUI() {
        // Initialize the now-corrected helper classes
        bottomOptions = new BottomOptions(this, musicUtility, database);
        playlistDialog = new PlaylistDialog(this, database, this::loadPlaylistAndPlay, this::loadAndShowPlaylist);
        bottomPlay.setOnClickListener(v -> handlePlayPauseClick());

        ImageButton bottomOptionsButton = findViewById(R.id.bottom_options);
        bottomOptions.handleBottomOptions(bottomOptionsButton);


        ImageButton topOptionsButton = findViewById(R.id.top_options_button);
        topOptionsButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(MainActivity.this, topOptionsButton);
            popup.getMenuInflater().inflate(R.menu.main_top_menu, popup.getMenu());

            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_scan_library) {
                    analyzeAllTracks();
                    return true;
                }
                return false;
            });
            popup.show();
        });

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


    private void loadPlaylistAndPlay(String playlistName) {
        currentPlaylistName = playlistName;

        // Update the state of helper classes
        bottomOptions.setPlaylistName(playlistName);
        trackAdapter.setCurrentPlaylistName(playlistName);

        executor.execute(() -> {
            PlaylistWithTracks pwt = database.playlistDao().getPlaylistWithTracks(playlistName);
            List<Track> playlistTracks = (pwt != null) ? pwt.tracks : new ArrayList<>();

            if (currentSortOrder == SortingOrder.A_TO_Z) {
                playlistTracks.sort((t1, t2) -> t1.title.compareToIgnoreCase(t2.title));
            } else {
                playlistTracks.sort((t1, t2) -> Long.compare(t2.dateModified, t1.dateModified));
            }

            currentlyDisplayedTracks = new ArrayList<>(playlistTracks);

            handler.post(() -> {
                trackAdapter.updateList(currentlyDisplayedTracks);
                if (!playlistTracks.isEmpty()) {
                    musicUtility.playList(playlistTracks, false);
                    updatePlayButtonIcon();
                    Toast.makeText(MainActivity.this, "Playing: " + playlistName, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Playlist '" + playlistName + "' is empty.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void handlePlayPauseClick() {
        if (musicUtility.isPlaying()) {
            musicUtility.pause();
        } else {
            musicUtility.resume();
        }
        updatePlayButtonIcon();
    }

    public void updatePlayButtonIcon() {
        if (musicUtility.isPlaying()) {
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
        musicUtility.stopAndCancel();
        unregisterReceiver(noisyReceiver);
        executor.shutdown();
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