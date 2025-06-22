package com.jochengehtab.musicplayer.MainActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
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
import com.jochengehtab.musicplayer.Music.OnPlaybackStateListener;
import com.jochengehtab.musicplayer.MusicList.Track;
import com.jochengehtab.musicplayer.MusicList.TrackAdapter;
import com.jochengehtab.musicplayer.R;
import com.jochengehtab.musicplayer.Utility.FileManager;
import com.jochengehtab.musicplayer.Utility.JSON;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    public static final String PREFS_NAME = "music_prefs";
    public static final String KEY_TREE_URI = "tree_uri";
    private final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
    public static JSON timestampsConfig;

    private Uri musicDirectoryUri;
    private FileManager fileManager;
    private MusicPlayer musicPlayer;
    private MusicUtility musicUtility;

    private SharedPreferences prefs;
    private ActivityResultLauncher<Uri> pickDirectoryLauncher;
    private TrackAdapter adapter;

    private Track lastTrack;

    public static boolean isMixPlaying = false;
    private TextView bottomTitle;
    private BottomPlayButton bottomPlay;
    private OnPlaybackStateListener playbackListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Objects.requireNonNull(getSupportActionBar()).hide();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        restorePreferences();

        musicUtility = new MusicUtility(this);
        musicPlayer = new MusicPlayer(musicUtility, this::updateBottomTitle, this::updateBottomPlay);

        initFolderChooser();

        // Music List
        RecyclerView musicList = findViewById(R.id.musicList);

        // UI refs
        MaterialButton chooseButton = findViewById(R.id.choose);
        bottomPlay = findViewById(R.id.bottom_play);
        bottomTitle = findViewById(R.id.bottom_title);

        // Playback listener toggles icon
        playbackListener = new OnPlaybackStateListener() {
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

        musicList.setLayoutManager(new LinearLayoutManager(this));
        musicList.setAdapter(adapter);

        // If we already had a folder, initialize JSON/FileManager and load
        if (musicDirectoryUri != null) {
            if (PermissionUtility.hasPersistedPermissions(musicDirectoryUri, getContentResolver())) {
                // Permissions are still valid, proceed as normal
                timestampsConfig = new JSON(this, PREFS_NAME, KEY_TREE_URI, "timestamps.json");
                fileManager = new FileManager(musicDirectoryUri, this, musicUtility);
                loadAndShowTracks();
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

        BottomOptions bottomOptions = new BottomOptions(this, musicUtility, musicPlayer, fileManager);

        chooseButton.setOnClickListener(v -> pickDirectoryLauncher.launch(null));

        bottomPlay.setOnClickListener(v -> updateBottomPlay(false));

        ImageButton bottomOptionsButton = findViewById(R.id.bottom_options);
        bottomOptions.handleBottomOptions(bottomOptionsButton, playbackListener, bottomPlay, bottomTitle);
    }

    public void updateBottomPlay(boolean forceUpdate) {

        bottomPlay.toggleIsPlayIconShowing();
        if (forceUpdate) {
            if (bottomPlay.isPlayIconShowing()) {
                bottomPlay.setImageResource(R.drawable.ic_play_arrow_white_24dp);
            } else {
                bottomPlay.setImageResource(R.drawable.ic_stop_white_24dp);
            }
            return;
        }

        if (musicUtility.isPlaying()) {
            musicUtility.pause();
            bottomPlay.setImageResource(R.drawable.ic_play_arrow_white_24dp);
        } else if (musicUtility.isInitialized()) {
            musicUtility.resume();
            bottomPlay.setImageResource(R.drawable.ic_stop_white_24dp);
        } else {
            musicUtility.play(lastTrack.uri(), playbackListener);
        }
    }

    public void updateBottomTitle(String newTitle) {
        bottomTitle.setText(newTitle);
    }

    private void loadAndShowTracks() {
        List<Track> tracks = fileManager.loadMusicFiles();
        adapter.updateList(tracks);
    }

    private void restorePreferences() {
        String uriStr = prefs.getString(KEY_TREE_URI, null);
        if (uriStr != null) {
            // Parse the URI
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
