package com.jochengehtab.musicplayer.MusicList;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.jochengehtab.musicplayer.Music.MusicUtility;
import com.jochengehtab.musicplayer.MusicList.Options.Trim;
import com.jochengehtab.musicplayer.R;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView.Adapter that binds a list of Track records into item_track.xml rows,
 * shows a three‐dot overflow menu, and uses DiffUtil to dispatch updates.
 * The “Edit” (action_rename) menu shows a trim dialog with sliders.
 */
public class TrackAdapter extends RecyclerView.Adapter<TrackViewHolder> {
    private final Context context;
    private final OnItemClickListener listener;
    private final List<Track> tracks = new ArrayList<>();
    private final Trim trim;

    public TrackAdapter(
            Context context,
            List<Track> initialTracks,
            OnItemClickListener listener,
            MusicUtility musicUtility
    ) {
        this.context = context;
        this.listener = listener;
        this.tracks.addAll(initialTracks);
        this.trim = new Trim(context, musicUtility);
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_track, parent, false);
        return new TrackViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(TrackViewHolder holder, int position) {
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
                    trim.showTrimDialog(current, position);
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
                // TODO
                if (id == R.id.action_reset) {
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
    public void updateList(List<Track> newList) {
        TrackDiffCallback diffCallback = new TrackDiffCallback(this.tracks, newList);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);

        this.tracks.clear();
        this.tracks.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }
}
