package com.jochengehtab.musicplayer.MusicList.Options;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog.Builder;

import com.jochengehtab.musicplayer.data.AppDatabase;
import com.jochengehtab.musicplayer.data.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class Rename {
    private final Context context;
    private final AppDatabase database;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public Rename(Context context, AppDatabase database) {
        this.context = context;
        this.database = database;
    }

    public void showRenameDialog(
            Track current,
            int position,
            List<Track> tracks,
            Consumer<List<Track>> updateCallback
    ) {
        Builder builder = new Builder(context);
        builder.setTitle("Edit Track Title");

        String fullName = current.title;
        int dotIndex = fullName.lastIndexOf('.');
        String baseName = (dotIndex >= 0) ? fullName.substring(0, dotIndex) : fullName;
        String extension = (dotIndex >= 0) ? fullName.substring(dotIndex) : "";

        final android.widget.EditText input = new android.widget.EditText(context);
        input.setText(baseName);
        input.setSelection(baseName.length());
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String newBaseName = input.getText().toString().trim();
            if (newBaseName.isEmpty() || newBaseName.equals(baseName)) {
                dialog.dismiss();
                return;
            }

            String newFullName = newBaseName + extension;
            try {
                Uri newUri = android.provider.DocumentsContract.renameDocument(
                        context.getContentResolver(),
                        Uri.parse(current.uri),
                        newFullName
                );

                if (newUri != null) {
                    // Update the track object with the new details
                    current.uri = newUri.toString();
                    current.title = newFullName;

                    // Update the database in the background
                    executor.execute(() -> {
                        database.trackDao().updateTrack(current);

                        // After saving, update the UI on the main thread
                        handler.post(() -> {
                            List<Track> updatedList = new ArrayList<>(tracks);
                            updatedList.set(position, current);
                            updateCallback.accept(updatedList);
                            Toast.makeText(context, "Rename successful", Toast.LENGTH_SHORT).show();
                        });
                    });
                } else {
                    Toast.makeText(context, "Rename failed.", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(context, "Error renaming: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.create().show();
    }
}