package com.jochengehtab.musicplayer.MainActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.jochengehtab.musicplayer.Music.MusicUtility;
import com.jochengehtab.musicplayer.Music.OnPlaybackStateListener;
import com.jochengehtab.musicplayer.MusicList.PlaylistActionsListener;
import com.jochengehtab.musicplayer.MusicList.PlaylistAdapter;
import com.jochengehtab.musicplayer.MusicList.Track;
import com.jochengehtab.musicplayer.MusicList.TrackAdapter;
import com.jochengehtab.musicplayer.R;
import com.jochengehtab.musicplayer.Utility.FileManager;
import com.jochengehtab.musicplayer.Utility.JSON;
import com.jochengehtab.musicplayer.Utility.PermissionUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {
    public static final String PREFS_NAME = "music_prefs";
    public static final String KEY_TREE_URI = "tree_uri";
    public static final String ALL_TRACKS_PLAYLIST_NAME = "All Tracks";
    public static JSON timestampsConfig;
    private final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
    private Uri musicDirectoryUri;
    private FileManager fileManager;
    private MusicUtility musicUtility;
    private SharedPreferences prefs;
    private ActivityResultLauncher<Uri> pickDirectoryLauncher;
    private TrackAdapter adapter;
    private TextView bottomTitle;
    private ImageButton bottomPlay;
    private SearchView searchView;
    private OnPlaybackStateListener playbackListener;
    private List<Track> allTracks = new ArrayList<>();
    private BottomOptions bottomOptions;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Objects.requireNonNull(getSupportActionBar()).hide();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        restorePreferences();

        musicUtility = new MusicUtility(this, this::updateBottomTitle, this::updatePlayButtonIcon);

        initFolderChooser();

        RecyclerView musicList = findViewById(R.id.musicList);
        MaterialButton chooseButton = findViewById(R.id.choose);
        bottomPlay = findViewById(R.id.bottom_play);
        bottomTitle = findViewById(R.id.bottom_title);

        ImageButton searchIcon = findViewById(R.id.search_icon);
        searchView = findViewById(R.id.track_search_view);

        // This listener ensures the icon is correct when playback stops naturally (e.g., song ends)
        playbackListener = new OnPlaybackStateListener() {
            @Override
            public void onPlaybackStarted() {
                // We can optionally call the update method here for robustness,
                // but the immediate UI change is handled on click.
                runOnUiThread(MainActivity.this::updatePlayButtonIcon);
            }

            @Override
            public void onPlaybackStopped() {
                runOnUiThread(MainActivity.this::updatePlayButtonIcon);
            }
        };

        adapter = new TrackAdapter(
                this,
                new ArrayList<>(),

                // Implementation of the OnItemClickListener
                track -> {
                    musicUtility.stopAndCancel();
                    bottomTitle.setText(track.title());
                    bottomPlay.setImageResource(R.drawable.ic_stop_white_24dp);

                    // Start playing the music
                    musicUtility.play(track.uri(), playbackListener);
                },
                musicUtility
        );

        musicList.setLayoutManager(new LinearLayoutManager(this));
        musicList.setAdapter(adapter);

        // If we already had a folder, initialize JSON/FileManager and load
        if (musicDirectoryUri != null) {
            if (PermissionUtility.hasPersistedPermissions(musicDirectoryUri, getContentResolver())) {
                // Permissions are still valid, proceed as normal
                try {
                    timestampsConfig = new JSON(this, PREFS_NAME, KEY_TREE_URI, "timestamps.json");
                    fileManager = new FileManager(musicDirectoryUri, this, allTracks);
                    loadAndShowTracks();
                } catch (Exception e) {
                    Log.e("MainActivity", "Failed to initialize FileManager or JSON config", e);
                    Toast.makeText(this, "Error initializing storage: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    // Clear the invalid URI so we don't keep trying
                    musicDirectoryUri = null;
                    fileManager = null;
                    prefs.edit().remove(KEY_TREE_URI).apply();
                }
            } else {
                // Permissions were lost or are invalid
                Toast.makeText(this, "Permission for folder was lost. Please choose it again.", Toast.LENGTH_LONG).show();
                // Clear the invalid URI so we don't keep trying
                musicDirectoryUri = null;
                prefs.edit().remove(KEY_TREE_URI).apply();
            }
        } else {
            Toast.makeText(this, "Please choose a music folder.", Toast.LENGTH_SHORT).show();
        }

        adapter.setFileManager(fileManager);

        bottomOptions = new BottomOptions(this, musicUtility, fileManager);

        chooseButton.setOnClickListener(v -> pickDirectoryLauncher.launch(null));
        bottomPlay.setOnClickListener(v -> handlePlayPauseClick());

        ImageButton bottomOptionsButton = findViewById(R.id.bottom_options);
        bottomOptions.handleBottomOptions(bottomOptionsButton, playbackListener, bottomPlay, bottomTitle);

        // Load the animations
        final Animation slideDown = AnimationUtils.loadAnimation(this, R.anim.slide_down_fade_in);
        final Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_out);

        // Set a listener on the slide-up animation to hide the view AFTER it finishes
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

        ImageButton burgerMenu = findViewById(R.id.burger_menu);
        burgerMenu.setOnClickListener(v -> showPlaylistDialog());

        searchIcon.setOnClickListener(v -> {
            if (searchView.getVisibility() == View.GONE) {
                // Show the search view with animation
                searchView.setVisibility(View.VISIBLE);
                searchView.startAnimation(slideDown);
                searchView.requestFocus();
            } else {
                // Hide the search view with animation
                searchView.setQuery("", false);
                searchView.startAnimation(slideUp);
            }
        });

        searchView.setOnCloseListener(() -> {
            searchView.setQuery("", false);
            searchView.startAnimation(slideUp);
            return true; // Return true to indicate we've handled the event
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

    private void filterTracks(String query) {
        List<Track> filteredTracks = allTracks.stream()
                .filter(track -> track.title().toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
        adapter.updateList(filteredTracks);
    }

    private void showPlaylistDialog() {
        if (fileManager == null) {
            Toast.makeText(this, "Please select a music directory first.", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_playlist_selector, null);
        RecyclerView playlistRv = dialogView.findViewById(R.id.playlist_list);
        ProgressBar progressBar = dialogView.findViewById(R.id.playlist_progress_bar);
        Button newPlaylistButton = dialogView.findViewById(R.id.button_create_playlist);
        Button cancelButton = dialogView.findViewById(R.id.button_cancel);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        // Show the dialog immediately with the progress bar visible
        dialog.show();

        executor.execute(() -> {
            // This is the slow operation
            List<String> playlists = fileManager.listPlaylists();
            playlists.add(0, ALL_TRACKS_PLAYLIST_NAME);

            // Post the result back to the main thread
            handler.post(() -> {
                // Hide the progress bar and show the list
                progressBar.setVisibility(View.GONE);
                playlistRv.setVisibility(View.VISIBLE);

                playlistRv.setLayoutManager(new LinearLayoutManager(this));

                PlaylistAdapter playlistAdapter = new PlaylistAdapter(this, playlists, new PlaylistActionsListener() {
                    @Override
                    public void onPlayClicked(String playlistName) {
                        dialog.dismiss();
                        loadPlaylistAndPlay(playlistName);
                    }

                    @Override
                    public void onSelectClicked(String playlistName) {
                        dialog.dismiss();
                        loadAndShowPlaylist(playlistName);
                        bottomOptions.setPlaylistName(playlistName);
                    }

                    @Override
                    public void onDeleteClicked(String playlistName) {
                        if (playlistName.equals(ALL_TRACKS_PLAYLIST_NAME)) {
                            Toast.makeText(MainActivity.this, "Cannot delete 'All Tracks' list.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Delete Playlist")
                                .setMessage("Are you sure you want to delete the playlist '" + playlistName + "'?")
                                .setPositiveButton("Delete", (d, which) -> {
                                    if (fileManager.deletePlaylist(playlistName)) {
                                        dialog.dismiss();
                                        showPlaylistDialog(); // Refresh the dialog
                                    }
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                });
                playlistRv.setAdapter(playlistAdapter);
            });
        });

        // Set up button listeners outside the background task
        newPlaylistButton.setOnClickListener(v -> {
            dialog.dismiss();
            showCreatePlaylistDialog();
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());
    }

    /**
     * Shows a dialog to get the name for a new playlist.
     */
    private void showCreatePlaylistDialog() {
        final EditText input = new EditText(this);
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Enter Playlist Name")
                .setView(input)
                .setPositiveButton("Create", (d, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        // Use the renamed method
                        if (fileManager.createPlaylist(name)) {
                            Toast.makeText(this, "Playlist '" + name + "' created.", Toast.LENGTH_SHORT).show();
                            // Reopen the playlist selector to show the new playlist
                            showPlaylistDialog();
                        }
                        // No "else" here, as createPlaylist now shows its own error toasts
                    } else {
                        Toast.makeText(this, "Playlist name cannot be empty.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", (d, which) -> {
                    // If cancelled, show the main playlist dialog again
                    showPlaylistDialog();
                })
                .show();
    }

    /**
     * Loads a playlist's tracks and updates the main RecyclerView to display them,
     * without starting playback. It also stops any currently playing music.
     *
     * @param playlistName The name of the playlist to load.
     */
    private void loadAndShowPlaylist(String playlistName) {
        musicUtility.stopAndCancel();

        List<Track> playlistTracks;
        if (ALL_TRACKS_PLAYLIST_NAME.equals(playlistName)) {
            playlistTracks = fileManager.loadMusicFiles();
        } else {
            playlistTracks = fileManager.loadTracksFromPlaylist(playlistName);
        }

        allTracks = new ArrayList<>(playlistTracks);
        adapter.updateList(allTracks);

        bottomTitle.setText(R.string.no_track_selected);

        updatePlayButtonIcon();
    }

    /**
     * Handles clicks on the main play/pause button.
     */
    private void handlePlayPauseClick() {

        // If a song is currently playing, pause it.
        if (musicUtility.isPlaying()) {
            musicUtility.pause();
        }
        // If a song is paused (initialized but not playing), resume it.
        else {
            musicUtility.resume();
        }

        // After any action, update the icon to reflect the new state.
        updatePlayButtonIcon();
    }

    /**
     * Updates the play/pause icon based on the app's state.
     * It shows a stop icon if a playlist is active OR a single track is playing.
     */
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

    /**
     * Loads tracks, updates the main RecyclerView, and starts playback for a playlist.
     * This handles the special "All Tracks" case and reloads the UI as requested.
     *
     * @param playlistName The name of the playlist to load and play.
     */
    private void loadPlaylistAndPlay(String playlistName) {
        List<Track> playlistTracks;

        if (ALL_TRACKS_PLAYLIST_NAME.equals(playlistName)) {
            // Case 1: The "All Tracks" virtual playlist is selected. Load all music files.
            playlistTracks = fileManager.loadMusicFiles();
        } else {
            // Case 2: A regular playlist (sub-folder) is selected.
            playlistTracks = fileManager.loadTracksFromPlaylist(playlistName);
        }

        allTracks = new ArrayList<>(playlistTracks);
        adapter.updateList(allTracks);

        // Now, start playing the loaded list.
        if (!playlistTracks.isEmpty()) {
            musicUtility.playList(playlistTracks, false);
            updatePlayButtonIcon();
            Toast.makeText(MainActivity.this, "Playing: " + playlistName, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(MainActivity.this, "Playlist '" + playlistName + "' is empty.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadAndShowTracks() {
        allTracks = fileManager.loadMusicFiles();
        adapter.updateList(allTracks);
    }

    private void restorePreferences() {
        String uriStr = prefs.getString(KEY_TREE_URI, null);
        if (uriStr != null) {
            musicDirectoryUri = Uri.parse(uriStr);
        }
    }

    private void initFolderChooser() {
        pickDirectoryLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri != null) {
                        getContentResolver().takePersistableUriPermission(uri, takeFlags);
                        prefs.edit().putString(KEY_TREE_URI, uri.toString()).apply();
                        musicDirectoryUri = uri;

                        try {
                            timestampsConfig = new JSON(MainActivity.this, PREFS_NAME, KEY_TREE_URI, "timestamps.json");
                            fileManager = new FileManager(musicDirectoryUri, MainActivity.this, allTracks);
                            adapter.setFileManager(fileManager); // Make sure adapter gets the new file manager
                            loadAndShowTracks();
                        } catch (Exception e) {
                            Log.e("MainActivity", "Failed to initialize on folder pick", e);
                            Toast.makeText(this, "Error setting up storage: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            // Reset state
                            musicDirectoryUri = null;
                            fileManager = null;
                            prefs.edit().remove(KEY_TREE_URI).apply();
                        }
                    } else {
                        Toast.makeText(this, "No folder selected.", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        musicUtility.stopAndCancel();
    }
}