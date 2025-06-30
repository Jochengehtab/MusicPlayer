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

        Integer[] timestamps = timestampsConfig.readArray(
                FileManager.getUriHash(track.uri()), Integer[].class
        );
        // timestamps[0] = saved start, [1] = saved end, [2] = full duration (sec)
        final int durationSec = Math.max(1, timestamps[2]);

        // Configure sliders to represent seconds directly
        seekStart.setMax(durationSec);
        seekEnd.setMax(durationSec);
        seekStart.setProgress(timestamps[0]);
        seekEnd.setProgress(timestamps[1]);
        valueStart.setText(String.valueOf(timestamps[0]));
        valueEnd.setText(String.valueOf(timestamps[1]));

        // When start thumb moves, clamp so it's always < end
        seekStart.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int startSec, boolean fromUser) {
                int endSec = seekEnd.getProgress();
                if (startSec >= endSec) {
                    startSec = Math.max(0, endSec - 1);
                    sb.setProgress(startSec);
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

        // When end thumb moves, clamp so it's always > start
        seekEnd.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int endSec, boolean fromUser) {
                int startSec = seekStart.getProgress();
                if (endSec <= startSec) {
                    endSec = startSec + 1;
                    sb.setProgress(endSec);
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
            int startSec = seekStart.getProgress();
            int endSec = seekEnd.getProgress();
            if (endSec <= startSec) {
                Toast.makeText(context, "End must be > start", Toast.LENGTH_SHORT).show();
                return;
            }
            musicUtility.playSegment(track.uri(), startSec, endSec);
        });

        // OK button: backup original and save new timestamps
        builder.setPositiveButton("OK", (dialog, which) -> {
            int startSec = seekStart.getProgress();
            int endSec = seekEnd.getProgress();

            // Only apply if trimmed
            if (startSec > 0 || endSec < durationSec) {
                try {
                    backupAndOverwrite(track);
                    timestampsConfig.write(
                            FileManager.getUriHash(track.uri()),
                            new int[]{startSec, endSec, durationSec}
                    );
                } catch (IOException e) {
                    Toast.makeText(
                            context,
                            "Error during backup/trim: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                }
            }
            dialog.dismiss();
        });

        // Cancel button
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.create().show();
    }

    private void backupAndOverwrite(Track track) throws IOException {
        SharedPreferences prefs =
                context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        String treeUriString = prefs.getString(MainActivity.KEY_TREE_URI, null);
        if (treeUriString == null) {
            throw new IOException("No SAF-folder URI saved; cannot locate parent folder.");
        }

        Uri musicTreeUri = Uri.parse(treeUriString);
        DocumentFile treeRoot = DocumentFile.fromTreeUri(context, musicTreeUri);
        if (treeRoot == null || !treeRoot.isDirectory()) {
            throw new IOException("Unable to resolve the SAF tree folder as a directory.");
        }

        DocumentFile backupsFolder = trimUtility.validateBackupFolder(treeRoot);

        // Find original file in tree
        String fullName = track.title();
        DocumentFile originalDoc = treeRoot.findFile(fullName);
        if (originalDoc == null) {
            originalDoc = DocumentFile.fromSingleUri(context, track.uri());
            if (originalDoc == null) {
                throw new IOException("Cannot locate the original file inside the granted tree.");
            }
        }

        Uri originalUri = originalDoc.getUri();
        String backupName = trimUtility.generateBackupName(fullName);

        // Only create a new backup if one doesn't already exist
        if (backupsFolder.findFile(backupName) == null) {
            trimUtility.backUpFile(
                    backupsFolder,
                    originalUri,
                    context.getContentResolver().getType(originalUri),
                    backupName
            );
        }

        Toast.makeText(
                context,
                "Backup created in “Backups/" + backupName + "”. (Trim not implemented.)",
                Toast.LENGTH_LONG
        ).show();
    }
}
