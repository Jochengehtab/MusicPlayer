package com.jochengehtab.musicplayer.MusicList.Options;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import com.jochengehtab.musicplayer.MainActivity;
import com.jochengehtab.musicplayer.Music.MusicUtility;
import com.jochengehtab.musicplayer.MusicList.Track;
import com.jochengehtab.musicplayer.R;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

public class Trim {
    private final Context context;
    private final MusicUtility musicUtility;

    public Trim(Context context, MusicUtility musicUtility) {
        this.context = context;
        this.musicUtility = musicUtility;
    }

    /**
     * Show a Dialog that allows the user to adjust start/end with sliders, preview it,
     * AND when “OK” is pressed, back up the original file and (placeholder) copy it back over itself.
     */
    public void showTrimDialog(Track track, int position) {
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(context);
        builder.setTitle("Trim Track");

        View dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_trim, null);
        builder.setView(dialogView);

        SeekBar seekStart = dialogView.findViewById(R.id.seek_start);
        SeekBar seekEnd = dialogView.findViewById(R.id.seek_end);
        TextView valueStart = dialogView.findViewById(R.id.value_start);
        TextView valueEnd = dialogView.findViewById(R.id.value_end);
        Button buttonPreview = dialogView.findViewById(R.id.button_preview);

        int durationMs;
        // Determine track duration in seconds
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(context, track.uri());
            String durationStr = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION);
            durationMs = Integer.parseInt(Objects.requireNonNull(durationStr));
            try {
                retriever.release();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final int durationSec = durationMs / 1000;

        // Initialize sliders and labels
        // 0 = start, 100 = end
        valueStart.setText("0");
        valueEnd.setText(String.valueOf(durationSec));
        seekStart.setProgress(0);
        seekEnd.setProgress(100);

        // SeekBar listeners enforce start < end
        seekStart.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int startSec = (durationSec * progress) / 100;
                int endSec = (durationSec * seekEnd.getProgress()) / 100;
                if (startSec >= endSec) {
                    int newProg = seekEnd.getProgress() - 1;
                    if (newProg < 0) newProg = 0;
                    sb.setProgress(newProg);
                    startSec = (durationSec * newProg) / 100;
                }
                valueStart.setText(String.valueOf(startSec));
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });

        seekEnd.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
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

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });

        // Preview button plays only the selected segment
        buttonPreview.setOnClickListener(previewView -> {
            int startSec = (durationSec * seekStart.getProgress()) / 100;
            int endSec = (durationSec * seekEnd.getProgress()) / 100;
            if (endSec <= startSec) {
                Toast.makeText(context, "End must be > start", Toast.LENGTH_SHORT).show();
                return;
            }
            musicUtility.playSegment(track.uri(), startSec, endSec);
        });

        // OK: only backup/trim if user changed the bounds from [0, durationSec]
        builder.setPositiveButton("OK", (dialog, which) -> {
            int startSec = (durationSec * seekStart.getProgress()) / 100;
            int endSec = (durationSec * seekEnd.getProgress()) / 100;

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

    private void backupAndOverwrite(Track track, int position) throws IOException {
        // Use the same PREFS_NAME and KEY_TREE_URI that MainActivity uses
        SharedPreferences prefs =
                context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);

        String treeUriString = prefs.getString(MainActivity.KEY_TREE_URI, null);
        if (treeUriString == null) {
            // If the user never picked a folder, we can’t proceed
            throw new IOException("No SAF-folder URI saved; cannot locate parent folder.");
        }

        Uri musicTreeUri = Uri.parse(treeUriString);
        // Get a DocumentFile representing the root of that tree
        DocumentFile treeRoot = DocumentFile.fromTreeUri(context, musicTreeUri);
        if (treeRoot == null || !treeRoot.isDirectory()) {
            throw new IOException("Unable to resolve the SAF tree folder as a directory.");
        }

        // Find or create the “Backups” subfolder under that tree
        final String BACKUPS_FOLDER_NAME = "Backups";
        DocumentFile backupsFolder = treeRoot.findFile(BACKUPS_FOLDER_NAME);
        if (backupsFolder == null) {
            // Didn’t exist yet, so create it
            backupsFolder = treeRoot.createDirectory(BACKUPS_FOLDER_NAME);
            if (backupsFolder == null) {
                throw new IOException("Failed to create “Backups” folder under the chosen tree.");
            }
        } else if (!backupsFolder.isDirectory()) {
            // We found something named “Backups” but it isn’t a folder
            throw new IOException("A non‐folder named “Backups” already exists in that tree.");
        }

        // Locate the DocumentFile corresponding to the original track
        // (We assume files are immediate children of treeRoot. If your files are nested
        //  in subfolders, you must walk the tree recursively instead of using findFile(...).)
        String fullName = track.title(); // e.g. “MySong.mp3”
        DocumentFile originalDoc = treeRoot.findFile(fullName);
        if (originalDoc == null) {
            // As a fallback, try fromSingleUri(...)
            originalDoc = DocumentFile.fromSingleUri(context, track.uri());
            if (originalDoc == null) {
                throw new IOException("Cannot locate the original file inside the granted tree.");
            }
            // Note: fromSingleUri(...)  getParentFile() will still be null,
            // so we continue using treeRoot as the “parent folder” for anything we create.
        }

        Uri originalUri = originalDoc.getUri();
        String mimeType = context.getContentResolver().getType(originalUri);

        // Build backupName
        int dotIndex = fullName.lastIndexOf('.');
        String baseName = (dotIndex >= 0 ? fullName.substring(0, dotIndex) : fullName);
        String extension = (dotIndex >= 0 ? fullName.substring(dotIndex) : "");
        String backupName = baseName + "_backup" + extension;

        // If a backup with that name already exists in “Backups”, skip creation.
        // Otherwise create the new file and copy bytes from the original to the backup.
        DocumentFile existingBackup = backupsFolder.findFile(backupName);
        if (existingBackup == null) {
            existingBackup = backupsFolder.createFile(Objects.requireNonNull(mimeType), backupName);
            if (existingBackup == null) {
                throw new IOException("Failed to create backup file \"" + backupName + "\".");
            }

            // Copy bytes: originalUri to existingBackup.getUri()
            try (
                    ParcelFileDescriptor pfdIn  = context.getContentResolver()
                            .openFileDescriptor(originalUri, "r");
                    ParcelFileDescriptor pfdOut = context.getContentResolver()
                            .openFileDescriptor(existingBackup.getUri(), "w");
                    FileInputStream  inStream   = new FileInputStream(Objects.requireNonNull(pfdIn).getFileDescriptor());
                    FileOutputStream outStream  = new FileOutputStream(Objects.requireNonNull(pfdOut).getFileDescriptor())
            ) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inStream.read(buffer)) > 0) {
                    outStream.write(buffer, 0, bytesRead);
                }
                outStream.flush();
            }
        }

        // Overwrite the original file with its own content (placeholder)
        // (This truncates the file, then writes all bytes back in. In a real “trim”
        //  implementation you’d decode→slice→re‐encode here instead.)
        try (
                ParcelFileDescriptor pfdInOverwrite  = context.getContentResolver()
                        .openFileDescriptor(originalUri, "r");
                ParcelFileDescriptor pfdOutOverwrite = context.getContentResolver()
                        .openFileDescriptor(originalUri, "w");
                FileInputStream  inOverwrite   = new FileInputStream(Objects.requireNonNull(pfdInOverwrite).getFileDescriptor());
                FileOutputStream outOverwrite  = new FileOutputStream(Objects.requireNonNull(pfdOutOverwrite).getFileDescriptor())
        ) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inOverwrite.read(buffer)) > 0) {
                outOverwrite.write(buffer, 0, bytesRead);
            }
            outOverwrite.flush();
        }

        Toast.makeText(
                context,
                "Backup created in “Backups/" + backupName + "”. (Trim not implemented.)",
                Toast.LENGTH_LONG
        ).show();
    }
}
