package com.jochengehtab.savemanger;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "music_prefs";
    private static final String KEY_TREE_URI = "tree_uri";

    private Uri musicDirectoryUri;
    private ListView musicList;
    private FileManager fileManager;
    private MusicPlayer musicPlayer;
    private MusicUtility musicUtility;

    private ActivityResultLauncher<Uri> pickDirectoryLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Hide action bar + lock orientation
        Objects.requireNonNull(getSupportActionBar()).hide();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        setContentView(R.layout.activity_main);

        // Initialize GUI elements
        musicList = findViewById(R.id.musicList);
        Button chooseButton = findViewById(R.id.choose);
        Button playButton = findViewById(R.id.play);

        musicUtility = new MusicUtility(this);

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

                    } else {
                        Toast.makeText(
                                this, "N o folder selected.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        fileManager = new FileManager(musicDirectoryUri, this);

        fileManager.loadMusicFiles();

        musicPlayer = new MusicPlayer(musicUtility);

        chooseButton.setOnClickListener(v -> launchDirectoryPicker());
        playButton.setOnClickListener(v -> {
            musicPlayer.playMix(fileManager.getTrackUris());
            System.out.println(fileManager.getTrackUris().size());
        });
    }

    /**
     * Launch the Storage Access Framework folder picker.
     */
    private void launchDirectoryPicker() {
        pickDirectoryLauncher.launch(null);
    }
        /*
        // Display file names in the ListView
        musicList.setAdapter(
                new ArrayAdapter<>(this,
                        android.R.layout.simple_list_item_1,
                        titles)
        );
         */
}
