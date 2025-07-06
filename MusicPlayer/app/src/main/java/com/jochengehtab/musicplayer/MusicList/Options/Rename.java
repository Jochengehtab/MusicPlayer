package com.jochengehtab.musicplayer.MusicList.Options;

import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog.Builder;

import com.jochengehtab.musicplayer.MusicList.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Rename {
    private final Context context;

    public Rename(Context context) {
        this.context = context;
    }

    /**
     * Shows a rename dialog and invokes updateCallback when rename succeeds.
     *
     * @param current        the track to rename
     * @param position       its index in the list
     * @param tracks         the current list of tracks
     * @param updateCallback called with the new list after successful rename
     */
    public void showRenameDialog(
            Track current,
            int position,
            List<Track> tracks,
            Consumer<List<Track>> updateCallback
    ) {
        Builder builder = new Builder(context);
        builder.setTitle("Edit Track Title");

        // Split base name and extension
        String fullName = current.title();
        int dotIndex = fullName.lastIndexOf('.');
        String baseName = (dotIndex >= 0) ? fullName.substring(0, dotIndex) : fullName;
        String extension = (dotIndex >= 0) ? fullName.substring(dotIndex) : "";

        final android.widget.EditText input = new android.widget.EditText(context);
        input.setText(baseName);
        input.setSelection(baseName.length());
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String newBaseName = input.getText().toString().trim();
            if (!newBaseName.isEmpty() && !newBaseName.equals(baseName)) {
                String newFullName = newBaseName + extension;
                try {
                    Uri newUri = android.provider.DocumentsContract.renameDocument(
                            context.getContentResolver(),
                            current.uri(),
                            newFullName
                    );
                    if (newUri != null) {
                        // Build updated list copy
                        List<Track> updatedList = new ArrayList<>(tracks);
                        // Replace the renamed track
                        updatedList.set(position, new Track(newUri, newFullName));
                        // Notify adapter via callback
                        updateCallback.accept(updatedList);
                    } else {
                        Toast.makeText(context,
                                "Rename failed.",
                                Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(context,
                            "Error renaming: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            }
            dialog.dismiss();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.create().show();
    }
}
