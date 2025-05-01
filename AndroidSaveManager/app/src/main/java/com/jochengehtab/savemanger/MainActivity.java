package com.jochengehtab.savemanger;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
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
    private Uri musicDirectoryUri;
    private MusicPlayer musicPlayer;
    private ListView musicList;

    // Launcher for “All files access” permission settings
    private ActivityResultLauncher<Intent> manageAllFilesLauncher;

    // Launcher for SAF directory picker
    private ActivityResultLauncher<Uri> pickDirectoryLauncher;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Hide action bar + lock orientation
        Objects.requireNonNull(getSupportActionBar()).hide();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        setContentView(R.layout.activity_main);

        musicPlayer = new MusicPlayer(getApplicationContext());
        musicList = findViewById(R.id.musicList);
        Button load = findViewById(R.id.load);

        // 1) Register launcher for MANAGE_EXTERNAL_STORAGE settings
        manageAllFilesLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // On return from Settings, re-check permission
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                            Environment.isExternalStorageManager()) {
                        // Granted → launch folder picker
                        launchDirectoryPicker();
                    } else {
                        // Still missing → inform user
                        Toast.makeText(this,
                                "All-files access is required to pick this folder.",
                                Toast.LENGTH_LONG).show();
                    }
                }
        );

        // 2) Register launcher for ACTION_OPEN_DOCUMENT_TREE
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
                        loadAndPlayAllMusic();
                    }
                }
        );

        // Wire up the button
        load.setOnClickListener(v -> checkAndRequestManageAllFiles());
    }

    /**
     * Check for “All files access” (Android 11+); if missing, send user to Settings,
     * otherwise proceed to folder picker.
     */
    private void checkAndRequestManageAllFiles() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && !Environment.isExternalStorageManager()) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            manageAllFilesLauncher.launch(intent);
        } else {
            launchDirectoryPicker();
        }
    }

    /**
     * Launch the Storage Access Framework folder picker.
     */
    private void launchDirectoryPicker() {
        // Passing null lets the system show the root-level picker UI
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
