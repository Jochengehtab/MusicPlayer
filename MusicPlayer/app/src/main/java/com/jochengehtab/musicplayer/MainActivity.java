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
    private static final String PREFS_NAME   = "music_prefs";
    private static final String KEY_TREE_URI = "tree_uri";

    private Uri musicDirectoryUri;
    private RecyclerView musicList;
    private FileManager fileManager;
    private MusicPlayer musicPlayer;
    private MusicUtility musicUtility;          // keep MusicUtility around

    private SharedPreferences prefs;

    private ActivityResultLauncher<Uri> pickDirectoryLauncher;
    private ArrayList<Track> tracks           = new ArrayList<>();
    private TrackAdapter adapter;             // our RecyclerView adapter

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Objects.requireNonNull(getSupportActionBar()).hide();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        setContentView(R.layout.activity_main);

        // 1) Initialize SharedPreferences
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // 2) Find UI elements
        musicList   = findViewById(R.id.musicList);
        MaterialButton chooseButton = findViewById(R.id.choose);
        MaterialButton playButton   = findViewById(R.id.play);

        // 3) Restore saved folder URI (if any)
        restorePreferences();

        // 4) Register SAF folder picker
        initFolderChooser();

        // 5) Prepare MusicUtility and MusicPlayer
        musicUtility = new MusicUtility(this);
        musicPlayer  = new MusicPlayer(musicUtility);

        // 6) Initialize FileManager (using the restored URI or null)
        fileManager = new FileManager(musicDirectoryUri, this);

        // 7) Load all music files into 'tracks'
        tracks = fileManager.loadMusicFiles();

        // 8) Set up RecyclerView
        musicList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TrackAdapter(
                tracks,
                track -> {
                    // ⇦ Cancel any ongoing mix so single‐track play doesn’t overlap:
                    musicPlayer.cancelMix();

                    // ⇦ MusicUtility.play(...) now requires a listener. Since this is a single‐track
                    // play, we can pass in an empty listener:
                    musicUtility.play(track.getUri(), () -> {
                        // No‐op on completion; you could update UI here if desired
                    });
                }
        );
        musicList.setAdapter(adapter);

        // 9) “Choose” button launches the SAF folder picker
        chooseButton.setOnClickListener(v -> launchDirectoryPicker());

        // 10) “Play” button reloads everything and then plays a random mix
        playButton.setOnClickListener(v -> {
            // Reload the track list in case files changed
            ArrayList<Track> freshList = fileManager.loadMusicFiles();
            tracks.clear();
            tracks.addAll(freshList);
            adapter.notifyDataSetChanged();

            // ⇦ Start the random‐mix (this will cancel any previous mix internally)
            musicPlayer.playMix(freshList);
        });
    }

    /** Restore the last‐saved directory URI (if there was one). */
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

    /** Configure the SAF “OpenDocumentTree” launcher. */
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

                        // Save this URI for next app-start
                        prefs.edit()
                                .putString(KEY_TREE_URI, uri.toString())
                                .apply();

                        // Re-initialize FileManager with the new URI
                        fileManager = new FileManager(musicDirectoryUri, MainActivity.this);

                        // Reload tracks from the newly chosen folder
                        ArrayList<Track> newTracks = fileManager.loadMusicFiles();
                        tracks.clear();
                        tracks.addAll(newTracks);
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

    /** Launch the SAF folder picker. */
    private void launchDirectoryPicker() {
        pickDirectoryLauncher.launch(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // ⇦ Clean up any ongoing playback or mix when the Activity is destroyed:
        musicPlayer.stopAndCancel();
    }
}
