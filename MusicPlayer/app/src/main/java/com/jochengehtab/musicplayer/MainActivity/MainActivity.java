package com.jochengehtab.musicplayer.MainActivity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jochengehtab.musicplayer.Dialog.PlaylistDialog;
import com.jochengehtab.musicplayer.Music.MusicUtility;
import com.jochengehtab.musicplayer.Music.OnPlaybackStateListener;
import com.jochengehtab.musicplayer.MusicList.TrackAdapter;
import com.jochengehtab.musicplayer.R;
import com.jochengehtab.musicplayer.Utility.SortingOrder;
import com.jochengehtab.musicplayer.data.AppDatabase;
import com.jochengehtab.musicplayer.data.PlaylistWithTracks;
import com.jochengehtab.musicplayer.data.Track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    public static final String ALL_TRACKS_PLAYLIST_NAME = "All Tracks";
    private static final int PERMISSION_REQUEST_CODE = 101;

    private MusicUtility musicUtility;
    private TrackAdapter trackAdapter;
    private TextView bottomTitle;
    private ImageButton bottomPlay;
    private SearchView searchView;
    private OnPlaybackStateListener playbackListener;
    private List<Track> currentlyDisplayedTracks = new ArrayList<>();
    private PlaylistDialog playlistDialog;
    private SortingOrder currentSortOrder = SortingOrder.A_TO_Z;
    private String currentPlaylistName = ALL_TRACKS_PLAYLIST_NAME;

    private ProgressBar updateProgressBar;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final BecomingNoisyReceiver noisyReceiver = new BecomingNoisyReceiver();
    private final IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

    private AppDatabase database;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Objects.requireNonNull(getSupportActionBar()).hide();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        setContentView(R.layout.activity_main);

        // Initialize Room Database
        database = AppDatabase.getDatabase(this);

        musicUtility = new MusicUtility(this, this::updateBottomTitle, this::updatePlayButtonIcon);

        RecyclerView musicList = findViewById(R.id.musicList);
        bottomPlay = findViewById(R.id.bottom_play);
        bottomTitle = findViewById(R.id.bottom_title);
        updateProgressBar = findViewById(R.id.update_progress_bar);
        ImageButton searchIcon = findViewById(R.id.search_icon);
        searchView = findViewById(R.id.track_search_view);
        ImageButton sortButton = findViewById(R.id.sort_button);

        playbackListener = new OnPlaybackStateListener() {
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
                    musicUtility.play(Uri.parse(track.uri), playbackListener);
                },
                musicUtility,
                database // Pass database instance to adapter
        );

        musicList.setLayoutManager(new LinearLayoutManager(this));
        musicList.setAdapter(trackAdapter);

        // Check for permissions and load music
        if (hasPermissions()) {
            scanAndLoadMusic();
        } else {
            requestPermissions();
        }

        // Setup UI components
        setupUI();
        registerReceiver(noisyReceiver, intentFilter);
    }

    private void setupUI() {
        // Note: BottomOptions and PlaylistDialog will need to be updated to use the database
        // instead of the old FileManager. This is a placeholder for that logic.
        // BottomOptions bottomOptions = new BottomOptions(this, musicUtility, database);
        // playlistDialog = new PlaylistDialog(database, this, bottomOptions, this::loadPlaylistAndPlay, this::loadAndShowPlaylist);

        bottomPlay.setOnClickListener(v -> handlePlayPauseClick());

        ImageButton bottomOptionsButton = findViewById(R.id.bottom_options);
        // bottomOptions.handleBottomOptions(bottomOptionsButton, playbackListener, bottomPlay, bottomTitle);

        final Animation slideDown = AnimationUtils.loadAnimation(this, R.anim.slide_down_fade_in);
        final Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_out);

        slideUp.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) {}
            @Override public void onAnimationEnd(Animation animation) { searchView.setVisibility(View.GONE); }
            @Override public void onAnimationRepeat(Animation animation) {}
        });

        ImageButton burgerMenu = findViewById(R.id.burger_menu);
        // burgerMenu.setOnClickListener(v -> playlistDialog.showPlaylistDialog());

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
            @Override public boolean onQueryTextSubmit(String query) { return false; }
            @Override public boolean onQueryTextChange(String newText) {
                filterTracks(newText);
                return true;
            }
        });
    }

    /**
     * Scans MediaStore for all audio files and updates the "All Tracks" playlist in the database.
     */
    private void scanAndLoadMusic() {
        updateProgressBar.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            // Step 1: Query MediaStore for all music files
            List<Track> mediaStoreTracks = new ArrayList<>();
            String[] projection = {
                    MediaStore.Audio.Media.DATA, // The URI
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
                    int uriColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                    int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                    int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                    int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                    int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                    int dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED);

                    while (cursor.moveToNext()) {
                        mediaStoreTracks.add(new Track(
                                cursor.getString(uriColumn),
                                cursor.getString(titleColumn),
                                cursor.getString(artistColumn),
                                cursor.getString(albumColumn),
                                cursor.getLong(durationColumn),
                                cursor.getLong(dateModifiedColumn)
                        ));
                    }
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Error scanning MediaStore", e);
            }

            // Step 2: Insert all scanned tracks into the database.
            // onConflict = REPLACE ensures that track details are updated if they change.
            database.trackDao().insertAll(mediaStoreTracks);

            // Step 3: Load the default playlist to the UI
            handler.post(() -> {
                updateProgressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Library Loaded.", Toast.LENGTH_SHORT).show();
                loadAndShowPlaylist(currentPlaylistName); // Load the default or last-viewed playlist
            });
        });
    }

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
            loadAndShowPlaylist(currentPlaylistName); // Reload with new sort order
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
     * Loads a playlist's tracks from the database and updates the RecyclerView.
     */
    private void loadAndShowPlaylist(String playlistName) {
        musicUtility.stopAndCancel();
        currentPlaylistName = playlistName;

        executor.execute(() -> {
            List<Track> playlistTracks;
            if (playlistName.equals(ALL_TRACKS_PLAYLIST_NAME)) {
                playlistTracks = database.trackDao().getAllTracks();
            } else {
                PlaylistWithTracks pwt = database.playlistDao().getPlaylistWithTracks(playlistName);
                playlistTracks = (pwt != null) ? pwt.tracks : new ArrayList<>();
            }

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

    /**
     * Loads a playlist and starts playback immediately.
     */
    private void loadPlaylistAndPlay(String playlistName) {
        executor.execute(() -> {
            List<Track> playlistTracks;
            if (playlistName.equals(ALL_TRACKS_PLAYLIST_NAME)) {
                playlistTracks = database.trackDao().getAllTracks();
            } else {
                PlaylistWithTracks pwt = database.playlistDao().getPlaylistWithTracks(playlistName);
                playlistTracks = (pwt != null) ? pwt.tracks : new ArrayList<>();
            }

            // Apply sorting
            if (currentSortOrder == SortingOrder.A_TO_Z) {
                Collections.sort(playlistTracks, (t1, t2) -> t1.title.compareToIgnoreCase(t2.title));
            } else { // MOST_RECENT
                Collections.sort(playlistTracks, (t1, t2) -> Long.compare(t2.dateModified, t1.dateModified));
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

    // --- Permission Handling ---

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

    // --- Lifecycle and Broadcast Receiver ---

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        musicUtility.stopAndCancel();
        unregisterReceiver(noisyReceiver);
        executor.shutdown();
    }
}