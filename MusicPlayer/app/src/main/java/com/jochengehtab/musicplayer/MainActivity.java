package com.jochengehtab.musicplayer;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.jochengehtab.musicplayer.Music.MusicPlayer;
import com.jochengehtab.musicplayer.Music.MusicUtility;
import com.jochengehtab.musicplayer.Music.OnPlaybackStateListener;
import com.jochengehtab.musicplayer.MusicList.Track;
import com.jochengehtab.musicplayer.MusicList.TrackAdapter;
import com.jochengehtab.musicplayer.Utility.FileManager;
import com.jochengehtab.musicplayer.Utility.JSON;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

    private Track lastTrack;  // the track to re‐play / stop

    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Objects.requireNonNull(getSupportActionBar()).hide();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        restorePreferences();

        musicUtility = new MusicUtility(this);
        musicPlayer = new MusicPlayer(musicUtility);

        initFolderChooser();

        // RecyclerView + empty adapter
        RecyclerView musicList = findViewById(R.id.musicList);
        musicList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TrackAdapter(
                this,
                new ArrayList<>(),
                track -> {
                },      // will be replaced below
                musicUtility
        );
        musicList.setAdapter(adapter);

        // UI refs
        MaterialButton chooseBtn = findViewById(R.id.choose);
        ImageButton bottomPlay = findViewById(R.id.bottom_play);
        TextView bottomTitle = findViewById(R.id.bottom_title);

        // Playback listener toggles icon
        OnPlaybackStateListener playbackListener = new OnPlaybackStateListener() {
            @Override
            public void onPlaybackStarted() {
                runOnUiThread(() ->
                        bottomPlay.setImageResource(R.drawable.ic_stop_white_24dp)
                );
            }

            @Override
            public void onPlaybackStopped() {
                runOnUiThread(() ->
                        bottomPlay.setImageResource(R.drawable.ic_play_arrow_white_24dp)
                );
            }
        };

        // Re‐set adapter so we can call play(...)
        adapter = new TrackAdapter(
                this,
                new ArrayList<>(),
                track -> {
                    musicPlayer.cancelMix();
                    lastTrack = track;
                    bottomTitle.setText(track.title());
                    musicUtility.play(track.uri(), playbackListener);
                },
                musicUtility
        );
        musicList.setAdapter(adapter);

        // If we already had a folder, initialize JSON/FileManager and load
        if (musicDirectoryUri != null) {
            timestampsConfig = new JSON(this, PREFS_NAME, KEY_TREE_URI, "timestamps.json");
            fileManager = new FileManager(musicDirectoryUri, this, musicUtility);
            loadAndShowTracks();
        } else {
            Toast.makeText(this, "Please choose a music folder.", Toast.LENGTH_SHORT).show();
        }

        chooseBtn.setOnClickListener(v -> pickDirectoryLauncher.launch(null));

        bottomPlay.setOnClickListener(v -> {
            if (lastTrack == null) {
                Toast.makeText(this, "No track selected.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (musicUtility.isPlaying()) {
                musicUtility.pause();
                bottomPlay.setImageResource(R.drawable.ic_play_arrow_white_24dp);
            } else if (musicUtility.isPaused()) {
                musicUtility.resume();
                bottomPlay.setImageResource(R.drawable.ic_stop_white_24dp);
            } else {
                musicUtility.play(lastTrack.uri(), playbackListener);
            }
        });

        ImageButton bottomOptions = findViewById(R.id.bottom_options);
        bottomOptions.setOnClickListener(v -> {
            // 1) Build a CharSequence[] from resources
            CharSequence[] items = getResources().getTextArray(R.array.playback_options);

            // 2) Determine which item is currently selected (or -1)
            int checkedItem = musicPlayer.isLooping() ? 0
                    : musicPlayer.isMixing() ? 1
                    : -1;

            // 3) Build the dialog
            new AlertDialog.Builder(this)
                    .setTitle("Playback Mode")
                    .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                        // Called when the user taps an option
                        dialog.dismiss();
                        if (which == 0) {
                            // Loop selected
                            boolean looping = musicPlayer.toggleLoop();
                            Toast.makeText(this,
                                    looping ? "Loop ON" : "Loop OFF",
                                    Toast.LENGTH_SHORT).show();
                        } else if (which == 1) {
                            // Mix selected
                            if (fileManager != null) {
                                // enable mix mode and start mix
                                musicPlayer.playMix(fileManager.loadMusicFiles());
                                Toast.makeText(this, "Mix started", Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void loadAndShowTracks() {
        List<Track> tracks = fileManager.loadMusicFiles();
        adapter.updateList(tracks);
    }

    private void restorePreferences() {
        String uriStr = prefs.getString(KEY_TREE_URI, null);
        if (uriStr != null) {
            Uri saved = Uri.parse(uriStr);
            getContentResolver().takePersistableUriPermission(
                    saved, Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
            musicDirectoryUri = saved;
        }
    }

    private void initFolderChooser() {
        pickDirectoryLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri != null) {
                        getContentResolver().takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
                        prefs.edit().putString(KEY_TREE_URI, uri.toString()).apply();
                        musicDirectoryUri = uri;

                        timestampsConfig = new JSON(
                                MainActivity.this,
                                PREFS_NAME,
                                KEY_TREE_URI,
                                "timestamps.json"
                        );
                        fileManager = new FileManager(
                                musicDirectoryUri, MainActivity.this, musicUtility
                        );
                        loadAndShowTracks();
                    } else {
                        Toast.makeText(this, "No folder selected.", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        musicPlayer.stopAndCancel();
    }
}
