package com.jochengehtab.musicplayer;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
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

    /**
     * Show a Dialog that allows the user to adjust start/end with sliders, preview it, and (optionally) apply trimming.
     */
    private void showTrimDialog(Track track, int position) {
        // 1) Inflate dialog_trim.xml
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(context);
        builder.setTitle("Trim Track");

        View dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_trim, null);
        builder.setView(dialogView);

        // 2) Find dialog views
        SeekBar seekStart = dialogView.findViewById(R.id.seek_start);
        SeekBar seekEnd = dialogView.findViewById(R.id.seek_end);
        TextView valueStart = dialogView.findViewById(R.id.value_start);
        TextView valueEnd = dialogView.findViewById(R.id.value_end);
        Button buttonPreview = dialogView.findViewById(R.id.button_preview);

        // 3) Determine track duration (in seconds) via MediaMetadataRetriever
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

        // 4) Initialize End slider value to track's full length
        valueStart.setText("0");
        valueEnd.setText(String.valueOf(durationSec));
        seekStart.setProgress(0);
        seekEnd.setProgress(100);

        // 5) SeekBar listeners to update labels and enforce start < end
        seekStart.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int startSec = (durationSec * progress) / 100;
                // If start >= end, clamp
                int endProgress = seekEnd.getProgress();
                int endSec = (durationSec * endProgress) / 100;
                if (startSec >= endSec) {
                    int newStartProgress = endProgress - 1;
                    if (newStartProgress < 0) newStartProgress = 0;
                    sb.setProgress(newStartProgress);
                    startSec = (durationSec * newStartProgress) / 100;
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
                // If end <= start, clamp
                int startProgress = seekStart.getProgress();
                int startSec2 = (durationSec * startProgress) / 100;
                if (endSec <= startSec2) {
                    int newEndProgress = startProgress + 1;
                    if (newEndProgress > 100) newEndProgress = 100;
                    sb.setProgress(newEndProgress);
                    endSec = (durationSec * newEndProgress) / 100;
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

        // 6) Preview button uses playSegment()
        buttonPreview.setOnClickListener(previewView -> {
            int startProgress = seekStart.getProgress();
            int endProgress = seekEnd.getProgress();
            int startSec = (durationSec * startProgress) / 100;
            int endSec = (durationSec * endProgress) / 100;
            if (endSec <= startSec) {
                Toast.makeText(context,
                        "End must be > start", Toast.LENGTH_SHORT).show();
                return;
            }
            musicUtility.playSegment(track.uri(), startSec, endSec);
        });

        // 7) OK / Cancel
        builder.setPositiveButton("OK", (dialog, which) -> {
            // TODO: If desired, actually trim the file on disk and update Track title/URI
            dialog.dismiss();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.create().show();
    }
}
