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
import com.jochengehtab.musicplayer.Music.MusicPlayer;
import com.jochengehtab.musicplayer.Music.MusicUtility;
import com.jochengehtab.musicplayer.MusicList.Track;
import com.jochengehtab.musicplayer.MusicList.TrackAdapter;
import com.jochengehtab.musicplayer.Utility.FileManager;
import com.jochengehtab.musicplayer.Utility.JSON;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    public static final String PREFS_NAME = "music_prefs";
    public static final String KEY_TREE_URI = "tree_uri";
    public static JSON timestampsConfig;

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

        timestampsConfig = new JSON(this, PREFS_NAME, KEY_TREE_URI, "timestamps.json");

        // 5) Prepare MusicUtility and MusicPlayer
        musicUtility = new MusicUtility(this);
        musicPlayer = new MusicPlayer(musicUtility);

        // 6) Initialize FileManager (using the restored URI or null)
        if (musicDirectoryUri != null) {
            fileManager = new FileManager(musicDirectoryUri, this, musicUtility);
        }

        // 7) Load all music files into initial list (might be empty)
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
                    musicPlayer.cancelMix();
                    musicUtility.play(track.uri());
                },
                musicUtility
        );
        musicList.setAdapter(adapter);

        // If no folder was restored, prompt the user to choose one
        if (musicDirectoryUri == null) {
            Toast.makeText(this, "Please choose a music folder first.", Toast.LENGTH_SHORT).show();
        }

        // 9) “Choose” button launches the SAF folder picker
        chooseButton.setOnClickListener(v -> launchDirectoryPicker());

        // 10) “Play” button reloads everything and then plays a random mix
        playButton.setOnClickListener(v -> {
            if (musicDirectoryUri == null) {
                Toast.makeText(
                        MainActivity.this,
                        "No folder selected. Please choose a folder first.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
            List<Track> freshList = fileManager.loadMusicFiles();
            if (freshList.isEmpty()) {
                Toast.makeText(
                        MainActivity.this,
                        "No music files found in this folder.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
            adapter.updateList(freshList);
            musicPlayer.playMix(freshList);
        });
    }

    /**
     * Restore last‐saved directory URI (if any).
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
     * Configure SAF “OpenDocumentTree” launcher.
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
                        fileManager = new FileManager(musicDirectoryUri, MainActivity.this, musicUtility);

                        // Reload tracks from the newly chosen folder
                        List<Track> newTracks = fileManager.loadMusicFiles();
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
        musicPlayer.stopAndCancel();
    }
}
