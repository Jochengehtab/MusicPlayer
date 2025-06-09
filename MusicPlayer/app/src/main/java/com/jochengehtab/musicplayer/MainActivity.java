package com.jochengehtab.musicplayer;

import static com.jochengehtab.musicplayer.MainActivity.timestampsConfig;

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
import com.jochengehtab.musicplayer.MusicList.Track;
import com.jochengehtab.musicplayer.MusicList.TrackAdapter;
import com.jochengehtab.musicplayer.Utility.FileManager;
import com.jochengehtab.musicplayer.Utility.JSON;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    public static final String PREFS_NAME   = "music_prefs";
    public static final String KEY_TREE_URI = "tree_uri";
    public static JSON timestampsConfig;

    private Uri musicDirectoryUri;
    private FileManager fileManager;
    private MusicPlayer musicPlayer;
    private MusicUtility musicUtility;

    private SharedPreferences prefs;
    private ActivityResultLauncher<Uri> pickDirectoryLauncher;
    private TrackAdapter adapter;

    private boolean isPlaying = false;
    private boolean isPaused  = false;
    private Track lastTrack;  // the track to re‐play / stop

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Objects.requireNonNull(getSupportActionBar()).hide();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        setContentView(R.layout.activity_main);

        // 1) SharedPreferences
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // 2) Restore folder URI
        restorePreferences();

        // 3) SAF folder picker
        initFolderChooser();

        // 4) JSON config
        timestampsConfig = new JSON(this, PREFS_NAME, KEY_TREE_URI, "timestamps.json");

        // 5) MusicUtility + MusicPlayer
        musicUtility = new MusicUtility(this);
        musicPlayer  = new MusicPlayer(musicUtility);

        // 6) FileManager if URI exists
        if (musicDirectoryUri != null) {
            fileManager = new FileManager(musicDirectoryUri, this, musicUtility);
        }

        // 7) UI refs
        RecyclerView musicList    = findViewById(R.id.musicList);
        MaterialButton chooseBtn  = findViewById(R.id.choose);
        MaterialButton mixBtn     = findViewById(R.id.mix);
        ImageButton bottomPlay    = findViewById(R.id.bottom_play);
        TextView    bottomTitle   = findViewById(R.id.bottom_title);

        // 8) Define playbackListener BEFORE adapter creation
        MusicUtility.OnPlaybackStateListener playbackListener =
                new MusicUtility.OnPlaybackStateListener() {
                    @Override
                    public void onPlaybackStarted() {
                        runOnUiThread(() -> {
                            bottomPlay.setImageResource(R.drawable.ic_stop_white_24dp);
                            isPlaying = true;
                            isPaused  = false;
                        });
                    }
                    @Override
                    public void onPlaybackStopped() {
                        runOnUiThread(() -> {
                            bottomPlay.setImageResource(R.drawable.ic_play_arrow_white_24dp);
                            isPlaying = false;
                            isPaused  = false;
                        });
                    }
                };

        // 9) RecyclerView + empty adapter
        musicList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TrackAdapter(
                this,
                new ArrayList<>(),  // start empty
                track -> {
                    musicPlayer.cancelMix();
                    lastTrack = track;
                    bottomTitle.setText(track.title());
                    musicUtility.play(track.uri(), playbackListener);
                },
                musicUtility
        );
        musicList.setAdapter(adapter);

        // 10) If folder exists, load initial tracks
        if (musicDirectoryUri != null) {
            fileManager = new FileManager(musicDirectoryUri, this, musicUtility);
            loadAndShowTracks();
        } else {
            Toast.makeText(this, "Please choose a music folder.", Toast.LENGTH_SHORT).show();
        }

        // 11) Choose button
        chooseBtn.setOnClickListener(v -> launchDirectoryPicker());

        // 12) Mix button
        mixBtn.setOnClickListener(v -> {
            if (musicDirectoryUri == null) {
                Toast.makeText(this,
                        "No folder selected. Please choose one first.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            loadAndShowTracks();
            musicPlayer.playMix(fileManager.loadMusicFiles());
        });

        // 13) Bottom play/pause/resume toggle
        bottomPlay.setOnClickListener(v -> {
            if (lastTrack == null) {
                Toast.makeText(this, "No track selected.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isPlaying) {
                musicUtility.pause();
                bottomPlay.setImageResource(R.drawable.ic_play_arrow_white_24dp);
                isPaused  = true;
                isPlaying = false;
            } else if (isPaused) {
                musicUtility.resume();
                bottomPlay.setImageResource(R.drawable.ic_stop_white_24dp);
                isPaused  = false;
                isPlaying = true;
            } else {
                musicUtility.play(lastTrack.uri(), playbackListener);
            }
        });
    }

    /** Load tracks and update adapter */
    private void loadAndShowTracks() {
        List<Track> tracks = fileManager.loadMusicFiles();
        adapter.updateList(tracks);
    }

    /** Restore saved folder URI */
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

    /** Configure SAF folder picker */
    private void initFolderChooser() {
        pickDirectoryLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri != null) {
                        getContentResolver().takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
                        musicDirectoryUri = uri;
                        prefs.edit().putString(KEY_TREE_URI, uri.toString()).apply();

                        fileManager = new FileManager(musicDirectoryUri, this, musicUtility);
                        loadAndShowTracks();
                    } else {
                        Toast.makeText(this, "No folder selected.", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    /** Launch folder picker */
    private void launchDirectoryPicker() {
        pickDirectoryLauncher.launch(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        musicPlayer.stopAndCancel();
    }
}
