package com.jochengehtab.musicplayer;

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
import androidx.appcompat.app.AppCompatActivity;
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

        // UI refs
        MaterialButton chooseButton = findViewById(R.id.choose);
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

        musicList.setLayoutManager(new LinearLayoutManager(this));
        musicList.setAdapter(adapter);

        // If we already had a folder, initialize JSON/FileManager and load
        if (musicDirectoryUri != null) {
            timestampsConfig = new JSON(this, PREFS_NAME, KEY_TREE_URI, "timestamps.json");
            fileManager = new FileManager(musicDirectoryUri, this, musicUtility);
            loadAndShowTracks();
        } else {
            Toast.makeText(this, "Please choose a music folder.", Toast.LENGTH_SHORT).show();
        }

        chooseButton.setOnClickListener(v -> pickDirectoryLauncher.launch(null));

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
            PopupMenu popup = new PopupMenu(this, bottomOptions);
            popup.inflate(R.menu.bottom_bar_menu);

            // 1) Tell the group to be single‐choice
            popup.getMenu().setGroupCheckable(
                    R.id.group_playback_modes,
                    true,
                    true
            );

            // 2) Pre‐check the currently active mode, if any
            int checkedId = musicPlayer.isLooping()
                    ? R.id.action_loop
                    : musicPlayer.isMixing()
                    ? R.id.action_mix
                    : -1;

            if (checkedId != -1) {
                popup.getMenu().findItem(checkedId).setChecked(true);
            }

            // 3) Handle selections
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_loop) {
                    boolean looping = musicPlayer.toggleLoop();
                    // uncheck the other
                    popup.getMenu().findItem(R.id.action_mix).setChecked(false);
                    item.setChecked(looping);
                    musicUtility.loopMediaPlayer(playbackListener);
                    return true;

                } else if (id == R.id.action_mix) {
                    if (fileManager != null) {
                        musicPlayer.playMix(fileManager.loadMusicFiles());
                    }
                    popup.getMenu().findItem(R.id.action_loop).setChecked(false);
                    item.setChecked(true);
                    return true;
                }
                return false;
            });

            popup.show();
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
