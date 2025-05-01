package com.jochengehtab.savemanger;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "music_prefs";
    private static final String KEY_TREE_URI = "tree_uri";

    private Uri musicDirectoryUri;
    private MusicPlayer musicPlayer;
    private ListView musicList;

    // Launcher for SAF directory picker
    private ActivityResultLauncher<Uri> pickDirectoryLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Hide action bar + lock orientation
        Objects.requireNonNull(getSupportActionBar()).hide();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        setContentView(R.layout.activity_main);

        // Init player and views
        musicPlayer = new MusicPlayer(getApplicationContext());
        musicList = findViewById(R.id.musicList);
        Button load = findViewById(R.id.load);

        // 1) Restore previously chosen folder (if any)
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedUriString = prefs.getString(KEY_TREE_URI, null);
        if (savedUriString != null) {
            Uri savedUri = Uri.parse(savedUriString);
            // Re-take URI permission on startup
            getContentResolver().takePersistableUriPermission(
                    savedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
            musicDirectoryUri = savedUri;
            // Load & play immediately
            loadAndPlayAllMusic();
        }

        // 2) Register SAF folder picker launcher
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

                        // Save selection for next run
                        prefs.edit()
                                .putString(KEY_TREE_URI, uri.toString())
                                .apply();

                        loadAndPlayAllMusic();
                    } else {
                        Toast.makeText(this,
                                "No folder selected.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // 3) Wire up the button
        load.setOnClickListener(v -> {
            if (musicDirectoryUri == null) {
                // First time: ask user to pick a folder
                launchDirectoryPicker();
            } else {
                // Already have a folder: reload current contents
                loadAndPlayAllMusic();
            }
        });
    }

    /**
     * Launch the Storage Access Framework folder picker.
     */
    private void launchDirectoryPicker() {
        pickDirectoryLauncher.launch(null);
    }

    /**
     * Enumerate audio files in the chosen folder and play them sequentially.
     */
    private void loadAndPlayAllMusic() {
        if (musicDirectoryUri == null) return;

        DocumentFile pickedDir = DocumentFile.fromTreeUri(this, musicDirectoryUri);
        if (pickedDir == null || !pickedDir.isDirectory()) return;

        List<Uri> trackUris = new ArrayList<>();
        List<String> titles = new ArrayList<>();

        for (DocumentFile file : pickedDir.listFiles()) {
            if (file.isFile()) {
                String name = file.getName();
                if (name != null &&
                        (name.endsWith(".mp3") || name.endsWith(".wav"))) {
                    trackUris.add(file.getUri());
                    titles.add(name);
                }
            }
        }

        // Display file names in the ListView
        musicList.setAdapter(
                new ArrayAdapter<>(this,
                        android.R.layout.simple_list_item_1,
                        titles)
        );

        // Start playback
        musicPlayer.setPlaylist(trackUris);
        musicPlayer.playMusic();
    }
}
