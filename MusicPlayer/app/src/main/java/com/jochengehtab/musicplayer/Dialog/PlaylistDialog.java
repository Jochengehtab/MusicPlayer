package com.jochengehtab.musicplayer.Dialog;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jochengehtab.musicplayer.MainActivity.MainActivity;
import com.jochengehtab.musicplayer.MusicList.PlaylistActionsListener;
import com.jochengehtab.musicplayer.MusicList.PlaylistAdapter;
import com.jochengehtab.musicplayer.R;
import com.jochengehtab.musicplayer.data.AppDatabase;
import com.jochengehtab.musicplayer.data.Playlist;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class PlaylistDialog {

    private final Context context;
    private final AppDatabase database;
    private final Consumer<String> loadPlaylistAndPlay;
    private final Consumer<String> loadAndShowPlaylist;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final AlertDialog playlistDialog;
    private final AlertDialog createPlaylistDialog;

    private final ProgressBar progressBar;
    private final RecyclerView playlistRv;
    private final EditText createPlaylistInput;

    public PlaylistDialog(Context context, AppDatabase database, Consumer<String> loadPlaylistAndPlay, Consumer<String> loadAndShowPlaylist) {
        this.context = context;
        this.database = database;
        this.loadPlaylistAndPlay = loadPlaylistAndPlay;
        this.loadAndShowPlaylist = loadAndShowPlaylist;

        View playlistDialogView = LayoutInflater.from(context).inflate(R.layout.dialog_playlist_selector, null);
        this.playlistRv = playlistDialogView.findViewById(R.id.playlist_list);
        this.progressBar = playlistDialogView.findViewById(R.id.playlist_progress_bar);
        Button newPlaylistButton = playlistDialogView.findViewById(R.id.button_create_playlist);
        Button cancelButton = playlistDialogView.findViewById(R.id.button_cancel);

        this.playlistDialog = new AlertDialog.Builder(context)
                .setView(playlistDialogView)
                .create();

        this.createPlaylistInput = new EditText(context);
        this.createPlaylistDialog = new AlertDialog.Builder(context)
                .setTitle("Enter Playlist Name")
                .setView(this.createPlaylistInput)
                .setPositiveButton("Create", null)
                .setNegativeButton("Cancel", (d, which) -> showPlaylistDialog())
                .create();

        createPlaylistDialog.setOnShowListener(dialog -> {
            Button positiveButton = createPlaylistDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(view -> {
                String name = createPlaylistInput.getText().toString().trim();
                createPlaylist(name);
            });
        });

        newPlaylistButton.setOnClickListener(v -> {
            playlistDialog.dismiss();
            showCreatePlaylistDialog();
        });
        cancelButton.setOnClickListener(v -> playlistDialog.dismiss());
    }

    private void createPlaylist(String name) {
        if (name.isEmpty()) {
            Toast.makeText(context, "Playlist name cannot be empty.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Prevent creating a playlist with the reserved name
        if (name.equalsIgnoreCase(MainActivity.ALL_TRACKS_PLAYLIST_NAME)) {
            Toast.makeText(context, "'All Tracks' is a reserved name.", Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            Playlist existing = database.playlistDao().getPlaylistByName(name);
            if (existing != null) {
                handler.post(() -> Toast.makeText(context, "A playlist with that name already exists.", Toast.LENGTH_SHORT).show());
                return;
            }

            database.playlistDao().insertPlaylist(new Playlist(name));
            handler.post(() -> {
                Toast.makeText(context, "Playlist '" + name + "' created.", Toast.LENGTH_SHORT).show();
                createPlaylistDialog.dismiss();
                showPlaylistDialog();
            });
        });
    }

    public void showPlaylistDialog() {
        progressBar.setVisibility(View.VISIBLE);
        playlistRv.setVisibility(View.GONE);
        playlistDialog.show();

        executor.execute(() -> {
            List<String> playlists = database.playlistDao().getAllPlaylistNames();

            handler.post(() -> {
                progressBar.setVisibility(View.GONE);
                playlistRv.setVisibility(View.VISIBLE);
                playlistRv.setLayoutManager(new LinearLayoutManager(context));

                PlaylistAdapter playlistAdapter = new PlaylistAdapter(context, playlists, new PlaylistActionsListener() {
                    @Override
                    public void onPlayClicked(String playlistName) {
                        playlistDialog.dismiss();
                        loadPlaylistAndPlay.accept(playlistName);
                    }

                    @Override
                    public void onSelectClicked(String playlistName) {
                        playlistDialog.dismiss();
                        loadAndShowPlaylist.accept(playlistName);
                    }

                    @Override
                    public void onDeleteClicked(String playlistName) {
                        new AlertDialog.Builder(context)
                                .setTitle("Delete Playlist")
                                .setMessage("Are you sure you want to delete the playlist '" + playlistName + "'?")
                                .setPositiveButton("Delete", (d, which) -> deletePlaylist(playlistName))
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                });
                playlistRv.setAdapter(playlistAdapter);
            });
        });
    }

    private void deletePlaylist(String playlistName) {
        executor.execute(() -> {
            database.playlistDao().deletePlaylistByName(playlistName);
            handler.post(() -> {
                Toast.makeText(context, "Playlist '" + playlistName + "' deleted.", Toast.LENGTH_SHORT).show();
                playlistDialog.dismiss();
                showPlaylistDialog();
            });
        });
    }

    private void showCreatePlaylistDialog() {
        createPlaylistInput.setText("");
        createPlaylistDialog.show();
    }
}