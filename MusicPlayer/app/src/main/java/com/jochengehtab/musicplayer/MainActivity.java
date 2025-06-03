package com.jochengehtab.musicplayer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "music_prefs";
    private static final String KEY_TREE_URI = "tree_uri";

    private Uri musicDirectoryUri;
    private FileManager fileManager;
    private MusicPlayer musicPlayer;
    private MusicUtility musicUtility;

    private SharedPreferences prefs;

    private ActivityResultLauncher<Uri> pickDirectoryLauncher;
    private TrackAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Objects.requireNonNull(getSupportActionBar()).hide();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        setContentView(R.layout.activity_main);

        // 1) Initialize SharedPreferences
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // 2) Find UI elements
        RecyclerView musicList = findViewById(R.id.musicList);
        MaterialButton chooseButton = findViewById(R.id.choose);
        MaterialButton playButton = findViewById(R.id.play);

        // 3) Restore saved folder URI (if any)
        restorePreferences();

        // 4) Register SAF folder picker
        initFolderChooser();

        // 5) Prepare MusicUtility and MusicPlayer
        musicUtility = new MusicUtility(this);
        musicPlayer = new MusicPlayer(musicUtility);

        // 6) Initialize FileManager (using the restored URI or null)
        if (musicDirectoryUri != null) {
            fileManager = new FileManager(musicDirectoryUri, this);
        }

        // 7) Load all music files into an initial list (might be empty)
        List<Track> initialTracks = new ArrayList<>();
        if (fileManager != null) {
            initialTracks = fileManager.loadMusicFiles();
        }

        // 8) Set up RecyclerView and adapter
        musicList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TrackAdapter(
                this,
                initialTracks,
                track -> {
                    // Cancel any ongoing mix, then play this one track
                    musicPlayer.cancelMix();
                    musicUtility.play(track.uri(), () -> { /* no-op */ });
                }
        );
        musicList.setAdapter(adapter);

        // If no folder was restored, prompt the user to choose one immediately
        if (musicDirectoryUri == null) {
            Toast.makeText(this, "Please choose a music folder first.", Toast.LENGTH_SHORT).show();
        }

        // 9) “Choose” button launches the SAF folder picker
        chooseButton.setOnClickListener(v -> launchDirectoryPicker());

        // 10) “Play” button reloads everything and then plays a random mix
        playButton.setOnClickListener(v -> {
            // If no folder chosen yet, prompt user
            if (musicDirectoryUri == null) {
                Toast.makeText(
                        MainActivity.this,
                        "No folder selected. Please choose a folder first.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            // Reload the track list in case files changed
            List<Track> freshList = fileManager.loadMusicFiles();
            if (freshList.isEmpty()) {
                Toast.makeText(
                        MainActivity.this,
                        "No music files found in this folder.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            // Use DiffUtil‐based update instead of notifyDataSetChanged()
            adapter.updateList(freshList);

            // Start the random‐mix
            musicPlayer.playMix(freshList);
        });
    }

    /**
     * Restore the last‐saved directory URI (if there was one).
     */
    private void restorePreferences() {
        String savedUriString = prefs.getString(KEY_TREE_URI, null);
        if (savedUriString != null) {
            Uri savedUri = Uri.parse(savedUriString);
            getContentResolver().takePersistableUriPermission(
                    savedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
            musicDirectoryUri = savedUri;
        }
    }

    /**
     * Configure the SAF “OpenDocumentTree” launcher.
     */
    private void initFolderChooser() {
        pickDirectoryLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri != null) {
                        // Persist read permission
                        getContentResolver().takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
                        musicDirectoryUri = uri;

                        // Save this URI for next app‐start
                        prefs.edit()
                                .putString(KEY_TREE_URI, uri.toString())
                                .apply();

                        // Re‐initialize FileManager with the new URI
                        fileManager = new FileManager(musicDirectoryUri, MainActivity.this);

                        // Reload tracks from the newly chosen folder
                        List<Track> newTracks = fileManager.loadMusicFiles();
                        if (newTracks.isEmpty()) {
                            Toast.makeText(
                                    MainActivity.this,
                                    "No music files found in selected folder.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                        adapter.updateList(newTracks);

                    } else {
                        Toast.makeText(
                                MainActivity.this,
                                "No folder selected.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    /**
     * Launch the SAF folder picker.
     */
    private void launchDirectoryPicker() {
        pickDirectoryLauncher.launch(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up any ongoing playback or mix when the Activity is destroyed
        musicPlayer.stopAndCancel();
    }
}
