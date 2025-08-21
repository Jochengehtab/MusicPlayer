package com.jochengehtab.musicplayer.Dialog;

import static com.jochengehtab.musicplayer.MainActivity.MainActivity.ALL_TRACKS_PLAYLIST_NAME;

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

import com.jochengehtab.musicplayer.MainActivity.BottomOptions;
import com.jochengehtab.musicplayer.MusicList.PlaylistActionsListener;
import com.jochengehtab.musicplayer.MusicList.PlaylistAdapter;
import com.jochengehtab.musicplayer.R;
import com.jochengehtab.musicplayer.Utility.FileManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class PlaylistDialog {

    private final FileManager fileManager;
    private final Context context;
    private final BottomOptions bottomOptions;
    private final Consumer<String> loadPlaylistAndPlay;
    private final Consumer<String> loadAndShowPlaylist;

    public PlaylistDialog(FileManager fileManager, Context context, BottomOptions bottomOptions, Consumer<String> loadPlaylistAndPlay, Consumer<String> loadAndShowPlaylist) {
        this.fileManager = fileManager;
        this.context = context;
        this.bottomOptions = bottomOptions;
        this.loadPlaylistAndPlay = loadPlaylistAndPlay;
        this.loadAndShowPlaylist = loadAndShowPlaylist;
    }

    public void showPlaylistDialog() {
        if (fileManager == null) {
            Toast.makeText(context, "Please select a music directory first.", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_playlist_selector, null);

        RecyclerView playlistRv = dialogView.findViewById(R.id.playlist_list);
        ProgressBar progressBar = dialogView.findViewById(R.id.playlist_progress_bar);
        Button newPlaylistButton = dialogView.findViewById(R.id.button_create_playlist);
        Button cancelButton = dialogView.findViewById(R.id.button_cancel);

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .create();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        // Show the dialog immediately with the progress bar visible
        dialog.show();

        executor.execute(() -> {
            // This is the slow operation
            List<String> playlists = fileManager.listPlaylists();
            playlists.add(0, ALL_TRACKS_PLAYLIST_NAME);

            // Post the result back to the main thread
            handler.post(() -> {
                // Hide the progress bar and show the list
                progressBar.setVisibility(View.GONE);
                playlistRv.setVisibility(View.VISIBLE);

                playlistRv.setLayoutManager(new LinearLayoutManager(context));

                PlaylistAdapter playlistAdapter = new PlaylistAdapter(context, playlists, new PlaylistActionsListener() {
                    @Override
                    public void onPlayClicked(String playlistName) {
                        dialog.dismiss();
                        loadPlaylistAndPlay.accept(playlistName);
                    }

                    @Override
                    public void onSelectClicked(String playlistName) {
                        dialog.dismiss();
                        loadAndShowPlaylist.accept(playlistName);
                        bottomOptions.setPlaylistName(playlistName);
                        fileManager.setCurrentPlaylistName(playlistName);
                    }

                    @Override
                    public void onDeleteClicked(String playlistName) {
                        if (playlistName.equals(ALL_TRACKS_PLAYLIST_NAME)) {
                            Toast.makeText(context, "Cannot delete 'All Tracks' list.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        new AlertDialog.Builder(context)
                                .setTitle("Delete Playlist")
                                .setMessage("Are you sure you want to delete the playlist '" + playlistName + "'?")
                                .setPositiveButton("Delete", (d, which) -> {
                                    if (fileManager.deletePlaylist(playlistName)) {
                                        dialog.dismiss();

                                        // TODO probably an infinite loop right there, needs investigation
                                        showPlaylistDialog();
                                    }
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                });
                playlistRv.setAdapter(playlistAdapter);
            });
        });

        // Set up button listeners outside the background task
        newPlaylistButton.setOnClickListener(v -> {
            dialog.dismiss();
            showCreatePlaylistDialog();
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());
    }


    private void showCreatePlaylistDialog() {
        final EditText input = new EditText(context);
        new AlertDialog.Builder(context)
                .setTitle("Enter Playlist Name")
                .setView(input)
                .setPositiveButton("Create", (d, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        // Use the renamed method
                        if (fileManager.createPlaylist(name)) {
                            Toast.makeText(context, "Playlist '" + name + "' created.", Toast.LENGTH_SHORT).show();
                            // Reopen the playlist selector to show the new playlist
                            showPlaylistDialog();
                        }
                        // No "else" here, as createPlaylist now shows its own error toasts
                    } else {
                        Toast.makeText(context, "Playlist name cannot be empty.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", (d, which) -> {
                    // If cancelled, show the main playlist dialog again
                    showPlaylistDialog();
                })
                .show();
    }
}
