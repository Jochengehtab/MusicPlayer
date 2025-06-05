package com.jochengehtab.musicplayer;

import android.content.ContentResolver;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView.Adapter that binds a list of Track records into item_track.xml rows,
 * shows a three‐dot overflow menu, and uses DiffUtil to dispatch updates.
 * The “Edit” (action_rename) menu shows a trim dialog with sliders.
 */
public class TrackAdapter extends RecyclerView.Adapter<TrackViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Track track);
    }

    private final Context context;
    private final OnItemClickListener listener;
    private final MusicUtility musicUtility;
    private final List<Track> tracks = new ArrayList<>();

    public TrackAdapter(
            @NonNull Context context,
            @NonNull List<Track> initialTracks,
            @NonNull OnItemClickListener listener,
            @NonNull MusicUtility musicUtility
    ) {
        this.context = context;
        this.listener = listener;
        this.musicUtility = musicUtility;
        this.tracks.addAll(initialTracks);
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_track, parent, false);
        return new TrackViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        Track current = tracks.get(position);
        holder.titleText.setText(current.title());

        // 1) Row click = play full track
        holder.itemView.setOnClickListener(v -> listener.onItemClick(current));

        // 2) Overflow (three dots) click = show PopupMenu
        holder.overflowIcon.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, holder.overflowIcon);
            popup.inflate(R.menu.track_item_menu);

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.edit) {
                    showTrimDialog(current, position);
                    return true;
                } else if (id == R.id.action_rename) {
                    // === Rename logic using DocumentsContract.renameDocument ===
                    androidx.appcompat.app.AlertDialog.Builder builder =
                            new androidx.appcompat.app.AlertDialog.Builder(context);
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

                            // Use DocumentsContract.renameDocument(...) instead of renameTo(...)
                            try {
                                Uri newUri = android.provider.DocumentsContract.renameDocument(
                                        context.getContentResolver(),
                                        current.uri(),
                                        newFullName
                                );
                                if (newUri != null) {
                                    // Successful rename: update adapter’s list
                                    Track renamedTrack = new Track(newUri, newFullName);
                                    List<Track> updatedList = new ArrayList<>(tracks);
                                    updatedList.set(position, renamedTrack);
                                    updateList(updatedList);
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
                    androidx.appcompat.app.AlertDialog dialog = builder.create();
                    dialog.show();

                    return true;
                } if (id == R.id.action_reset) {
                    restoreFromBackup(current, position);
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    /**
     * Replace the adapter’s contents with newList, using DiffUtil
     * to compute minimal insert/remove/change operations.
     */
    public void updateList(@NonNull List<Track> newList) {
        TrackDiffCallback diffCallback = new TrackDiffCallback(this.tracks, newList);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

        this.tracks.clear();
        this.tracks.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    // ─── 1) TRIM DIALOG ───────────────────────────────────────────────────────────────
    /**
     * Show a Dialog that allows the user to adjust start/end with sliders, preview it,
     * AND when “OK” is pressed, back up the original file and (placeholder) copy it back over itself.
     */
    private void showTrimDialog(Track track, int position) {
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(context);
        builder.setTitle("Trim Track");

        View dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_trim, null);
        builder.setView(dialogView);

        SeekBar seekStart    = dialogView.findViewById(R.id.seek_start);
        SeekBar seekEnd      = dialogView.findViewById(R.id.seek_end);
        TextView valueStart  = dialogView.findViewById(R.id.value_start);
        TextView valueEnd    = dialogView.findViewById(R.id.value_end);
        Button  buttonPreview = dialogView.findViewById(R.id.button_preview);

        // Determine track duration (in seconds)
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(context, track.uri());
        String durationStr = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION);
        int durationMs = Integer.parseInt(durationStr);
        int durationSec = durationMs / 1000;
        try {
            retriever.release();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Initialize sliders (0% = start, 100% = end) and labels
        valueStart.setText("0");
        valueEnd.setText(String.valueOf(durationSec));
        seekStart.setProgress(0);
        seekEnd.setProgress(100);

        // SeekBar listeners enforce start < end
        seekStart.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int startSec = (durationSec * progress) / 100;
                int endSec   = (durationSec * seekEnd.getProgress()) / 100;
                if (startSec >= endSec) {
                    int newProg = seekEnd.getProgress() - 1;
                    if (newProg < 0) newProg = 0;
                    sb.setProgress(newProg);
                    startSec = (durationSec * newProg) / 100;
                }
                valueStart.setText(String.valueOf(startSec));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });

        seekEnd.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int endSec = (durationSec * progress) / 100;
                int startSec2 = (durationSec * seekStart.getProgress()) / 100;
                if (endSec <= startSec2) {
                    int newProg = seekStart.getProgress() + 1;
                    if (newProg > 100) newProg = 100;
                    sb.setProgress(newProg);
                    endSec = (durationSec * newProg) / 100;
                }
                valueEnd.setText(String.valueOf(endSec));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });

        // Preview button plays only the selected segment
        buttonPreview.setOnClickListener(previewView -> {
            int startSec = (durationSec * seekStart.getProgress()) / 100;
            int endSec   = (durationSec * seekEnd.getProgress()) / 100;
            if (endSec <= startSec) {
                Toast.makeText(context, "End must be > start", Toast.LENGTH_SHORT).show();
                return;
            }
            musicUtility.playSegment(track.uri(), startSec, endSec);
        });

        // OK: only backup/trim if user changed the bounds from [0, durationSec]
        builder.setPositiveButton("OK", (dialog, which) -> {
            int startSec = (durationSec * seekStart.getProgress()) / 100;
            int endSec   = (durationSec * seekEnd.getProgress()) / 100;

            // Check if anything actually changed
            if (startSec > 0 || endSec < durationSec) {
                try {
                    backupAndOverwrite(track, position);
                } catch (IOException e) {
                    Toast.makeText(context,
                            "Error during backup/trim: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            }
            // If slider still at full range (0 to durationSec), just dismiss
            dialog.dismiss();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.create().show();
    }


    /**
     * Backs up the original file (if no backup exists) and then overwrites the original
     * with its own content (placeholder for real trimming). Afterward, updates the adapter
     * so the displayed title/URI remain correct (no change).
     */
    private void backupAndOverwrite(Track track, int position) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        Uri originalUri = track.uri();

        // 1) Build backup name: original base + "_backup" + extension
        String fullName = track.title();
        int dotIndex = fullName.lastIndexOf('.');
        String baseName = (dotIndex >= 0) ? fullName.substring(0, dotIndex) : fullName;
        String extension = (dotIndex >= 0) ? fullName.substring(dotIndex) : "";
        String backupName = baseName + "_backup" + extension;

        // 2) Locate parent directory
        Uri parentUri = DocumentsContract.getTreeDocumentId(originalUri) != null
                ? DocumentsContract.buildDocumentUriUsingTree(
                originalUri, DocumentsContract.getDocumentId(originalUri))
                : null;
        // Simpler: use fromSingleUri().getParentFile() to find parent
        androidx.documentfile.provider.DocumentFile fileDoc =
                androidx.documentfile.provider.DocumentFile.fromSingleUri(context, originalUri);
        androidx.documentfile.provider.DocumentFile parentDir = fileDoc.getParentFile();
        if (parentDir == null || !parentDir.isDirectory()) {
            throw new IOException("Unable to find parent directory for backup");
        }

        // 3) Check if backup already exists
        androidx.documentfile.provider.DocumentFile existingBackup = null;
        for (androidx.documentfile.provider.DocumentFile f : parentDir.listFiles()) {
            if (backupName.equals(f.getName())) {
                existingBackup = f;
                break;
            }
        }

        // 4) If no backup, create one
        if (existingBackup == null) {
            existingBackup = parentDir.createFile(
                    resolver.getType(originalUri), backupName);
            if (existingBackup == null) {
                throw new IOException("Failed to create backup file");
            }
            // Copy contents → backup
            try (InputStream in = resolver.openInputStream(originalUri);
                 OutputStream out = resolver.openOutputStream(existingBackup.getUri())) {
                if (in == null || out == null) {
                    throw new IOException("Unable to open streams for backup");
                }
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        }

        // 5) Placeholder: overwrite original with its own content (no actual trim)
        // Real trimming would involve decoding / re-encoding using MediaExtractor/MediaMuxer.
        try (InputStream in = resolver.openInputStream(originalUri);
             OutputStream out = resolver.openOutputStream(originalUri)) {
            if (in == null || out == null) {
                throw new IOException("Unable to open streams to overwrite original");
            }
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }

        Toast.makeText(context, "Backup created as “" + backupName +
                "”. (Trim not implemented.)", Toast.LENGTH_LONG).show();

        // 6) No actual change in URI/title, but if you did change either, update adapter:
        // List<Track> updated = new ArrayList<>(tracks);
        // updated.set(position, new Track(originalUri, fullName));
        // updateList(updated);
    }

    // ─── 2) RESET FROM BACKUP ─────────────────────────────────────────────────────────
    /**
     * Restores the original file from its backup (if present). Overwrites the current file
     * with the contents of backup, then deletes the backup. Finally, updates adapter to show original.
     */
    private void restoreFromBackup(Track track, int position) {
        ContentResolver resolver = context.getContentResolver();
        Uri originalUri = track.uri();

        // Build backup name as above
        String fullName = track.title();
        int dotIndex = fullName.lastIndexOf('.');
        String baseName = (dotIndex >= 0) ? fullName.substring(0, dotIndex) : fullName;
        String extension = (dotIndex >= 0) ? fullName.substring(dotIndex) : "";
        String backupName = baseName + "_backup" + extension;

        androidx.documentfile.provider.DocumentFile fileDoc =
                androidx.documentfile.provider.DocumentFile.fromSingleUri(context, originalUri);
        androidx.documentfile.provider.DocumentFile parentDir = fileDoc.getParentFile();
        if (parentDir == null || !parentDir.isDirectory()) {
            Toast.makeText(context, "Cannot locate parent for reset", Toast.LENGTH_SHORT).show();
            return;
        }

        // Find backup
        androidx.documentfile.provider.DocumentFile backupFile = null;
        for (androidx.documentfile.provider.DocumentFile f : parentDir.listFiles()) {
            if (backupName.equals(f.getName())) {
                backupFile = f;
                break;
            }
        }
        if (backupFile == null) {
            Toast.makeText(context, "No backup found to restore", Toast.LENGTH_SHORT).show();
            return;
        }

        // Copy backup contents → original
        try (InputStream in = resolver.openInputStream(backupFile.getUri());
             OutputStream out = resolver.openOutputStream(originalUri)) {
            if (in == null || out == null) {
                throw new IOException("Failed to open streams for reset");
            }
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } catch (IOException e) {
            Toast.makeText(context, "Reset failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        // Delete the backup file
        if (!backupFile.delete()) {
            Toast.makeText(context,
                    "Reset succeeded, but failed to delete backup file.",
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(context, "File restored from backup.", Toast.LENGTH_SHORT).show();
        }

        // No change in URI/title; if you renamed in-trim, you might need to restore that too.
        // For now, adapter remains unchanged.
    }
}
