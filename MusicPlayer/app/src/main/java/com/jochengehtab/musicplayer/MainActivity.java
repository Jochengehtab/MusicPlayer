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
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "music_prefs";
    private static final String KEY_TREE_URI = "tree_uri";

    private Uri musicDirectoryUri;
    private RecyclerView musicList;
    private FileManager fileManager;
    private MusicPlayer musicPlayer;

    private SharedPreferences prefs;

    private ActivityResultLauncher<Uri> pickDirectoryLauncher;
    private ArrayList<Track> tracks = new ArrayList<>();
    private TrackAdapter adapter; // RecyclerView adapter

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Hide action bar and lock orientation
        Objects.requireNonNull(getSupportActionBar()).hide();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        setContentView(R.layout.activity_main);

        // 1) Initialize SharedPreferences
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // 2) Find UI elements
        musicList = findViewById(R.id.musicList);
        MaterialButton chooseButton = findViewById(R.id.choose);
        MaterialButton playButton = findViewById(R.id.play);

        // 3) Restore previously chosen folder (if any)
        restorePreferences();

        // 4) Register SAF folder picker
        initFolderChooser();

        // 5) Prepare MusicUtility and MusicPlayer
        MusicUtility musicUtility = new MusicUtility(this);
        musicPlayer = new MusicPlayer(musicUtility);

        // 6) Initialize FileManager (it internally uses musicDirectoryUri)
        fileManager = new FileManager(musicDirectoryUri, this);

        // 7) Load music files into 'tracks'
        tracks = fileManager.loadMusicFiles();

        // 8) Set up RecyclerView: LayoutManager + Adapter
        //    - LinearLayoutManager (vertical list)
        musicList.setLayoutManager(new LinearLayoutManager(this));
        //    - Adapter with our 'tracks' ArrayList
        adapter = new TrackAdapter(tracks);
        musicList.setAdapter(adapter);

        // 9) Button click: “Choose” launches folder picker
        chooseButton.setOnClickListener(v -> launchDirectoryPicker());

        // 10) Button click: “Play” uses the currently loaded tracks
        playButton.setOnClickListener(v -> {
            // Always reload before playing, in case files changed
            ArrayList<Track> freshList = fileManager.loadMusicFiles();
            tracks.clear();
            tracks.addAll(freshList);
            adapter.notifyDataSetChanged();

            // Now play the mix
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
            // Re‐take URI permission on startup
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

                        // 1) Re-create FileManager with the new directory
                        fileManager = new FileManager(musicDirectoryUri, MainActivity.this);

                        // 2) Reload tracks from the newly chosen folder
                        ArrayList<Track> newTracks = fileManager.loadMusicFiles();
                        tracks.clear();
                        tracks.addAll(newTracks);

                        // 3) Tell RecyclerView’s adapter that data changed
                        adapter.notifyDataSetChanged();
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
}
