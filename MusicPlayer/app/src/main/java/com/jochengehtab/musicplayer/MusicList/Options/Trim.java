package com.jochengehtab.musicplayer.MusicList.Options;

import static com.jochengehtab.musicplayer.MainActivity.MainActivity.timestampsConfig;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog.Builder;
import androidx.documentfile.provider.DocumentFile;

import com.jochengehtab.musicplayer.MainActivity.MainActivity;
import com.jochengehtab.musicplayer.Music.MusicUtility;
import com.jochengehtab.musicplayer.MusicList.Track;
import com.jochengehtab.musicplayer.R;
import com.jochengehtab.musicplayer.Utility.FileManager;

import java.io.IOException;

public class Trim {
    private final Context context;
    private final MusicUtility musicUtility;
    private final TrimUtility trimUtility;

    public Trim(Context context, MusicUtility musicUtility) {
        this.context = context;
        this.musicUtility = musicUtility;
        this.trimUtility = new TrimUtility(context);
    }

    /**
     * Show a Dialog that allows the user to adjust start/end with sliders, preview it,
     * AND when “OK” is pressed, back up the original file and (placeholder) copy it back over itself.
     */
    public void showTrimDialog(Track track) {
        Builder builder = new Builder(context);
        builder.setTitle("Trim Track");

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_trim, null);
        builder.setView(dialogView);

        // Initialize the UI
        SeekBar seekStart = dialogView.findViewById(R.id.seek_start);
        SeekBar seekEnd = dialogView.findViewById(R.id.seek_end);
        TextView valueStart = dialogView.findViewById(R.id.value_start);
        TextView valueEnd = dialogView.findViewById(R.id.value_end);
        Button buttonPreview = dialogView.findViewById(R.id.button_preview);

        Integer[] timestamps = timestampsConfig.readArray(FileManager.getUriHash(track.uri()), Integer[].class);
        assert timestamps.length > 1;

        final int durationSec = Math.max(1, timestamps[1]);

        // Initialize sliders and labels
        valueStart.setText(String.valueOf(timestamps[0]));
        valueEnd.setText(String.valueOf(timestamps[1]));
        seekStart.setProgress(timestamps[0]);
        seekEnd.setProgress(timestamps[1]);

        seekStart.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int endProg = seekEnd.getProgress();

                // Clamp to end - 1 if needed
                if (progress >= endProg) {
                    progress = endProg - 1;
                    if (progress < 0) progress = 0;
                    sb.setProgress(progress);
                }

                int startSec = (durationSec * progress) / 100;
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
                int startProg = seekStart.getProgress();
                // ceil() returns the smallest integer that is greater or equal to the argument 
                int minProgressFor1Sec = (int) Math.ceil(100.0 / durationSec);
                int minAllowed = startProg + minProgressFor1Sec;

                // Enforce lower bound for actual time range
                if (progress < minProgressFor1Sec) {
                    progress = minProgressFor1Sec;
                    sb.setProgress(progress);
                    return;
                }

                // Enforce that end is at least 1 second after start
                if (progress < minAllowed) {
                    progress = Math.min(minAllowed, 100);
                    sb.setProgress(progress);
                    return;
                }

                int endSec = (durationSec * progress) / 100;
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

        // OK Button
        builder.setPositiveButton("OK", (dialog, which) -> {
            int startSec = (durationSec * seekStart.getProgress()) / 100;
            int endSec = (durationSec * seekEnd.getProgress()) / 100;

            // Check if anything actually changed
            if (startSec > 0 || endSec < durationSec) {
                try {
                    backupAndOverwrite(track);
                    timestampsConfig.write(FileManager.getUriHash(track.uri()), new int[]{startSec, endSec});
                } catch (IOException e) {
                    Toast.makeText(context,
                            "Error during backup/trim: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            }
            // If slider still at full range (0 to durationSec), just dismiss
            dialog.dismiss();
        });

        // Chancel button
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.create().show();
    }

    private void backupAndOverwrite(Track track) throws IOException {
        // Use the same PREFS_NAME and KEY_TREE_URI that MainActivity uses
        SharedPreferences prefs =
                context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);

        String treeUriString = prefs.getString(MainActivity.KEY_TREE_URI, null);
        if (treeUriString == null) {
            throw new IOException("No SAF-folder URI saved; cannot locate parent folder.");
        }

        Uri musicTreeUri = Uri.parse(treeUriString);
        // Get a DocumentFile representing the root of that tree
        DocumentFile treeRoot = DocumentFile.fromTreeUri(context, musicTreeUri);
        if (treeRoot == null || !treeRoot.isDirectory()) {
            throw new IOException("Unable to resolve the SAF tree folder as a directory.");
        }

        DocumentFile backupsFolder = trimUtility.validateBackupFolder(treeRoot);

        // Locate the DocumentFile corresponding to the original track
        String fullName = track.title();
        DocumentFile originalDoc = treeRoot.findFile(fullName);
        if (originalDoc == null) {
            // Fallback
            originalDoc = DocumentFile.fromSingleUri(context, track.uri());
            if (originalDoc == null) {
                throw new IOException("Cannot locate the original file inside the granted tree.");
            }
        }

        Uri originalUri = originalDoc.getUri();
        String backupName = trimUtility.generateBackupName(fullName);

        // If a backup with that name already exists in “Backups”, skip creation.
        // Otherwise create the new file and copy bytes from the original to the backup.
        if (backupsFolder.findFile(backupName) == null) {
            trimUtility.backUpFile(backupsFolder,
                    originalUri, context.getContentResolver().getType(originalUri), backupName);
        }

        Toast.makeText(
                context,
                "Backup created in “Backups/" + backupName + "”. (Trim not implemented.)",
                Toast.LENGTH_LONG
        ).show();
    }
}
