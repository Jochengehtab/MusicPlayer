package com.jochengehtab.musicplayer.MusicList;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

// Import the new Track entity
import com.jochengehtab.musicplayer.data.Track;

import java.util.List;
import java.util.Objects;

/**
 * Computes the diff between two lists of Track so RecyclerView
 * can animate insertions/removals/updates instead of rebinding everything.
 */
public class TrackDiffCallback extends DiffUtil.Callback {
    private final List<Track> oldList;
    private final List<Track> newList;

    public TrackDiffCallback(@NonNull List<Track> oldList, @NonNull List<Track> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList.size();
    }

    /**
     * Two Tracks are considered the same item if they have the same database ID.
     * This is the most reliable way to check for identity.
     */
    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        // Compare the unique database ID
        return oldList.get(oldItemPosition).id == newList.get(newItemPosition).id;
    }

    /**
     * If the items are the same, this checks if their contents (the data displayed) have changed.
     * We now access fields directly (e.g., .title) instead of methods (e.g., .title()).
     */
    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        Track oldTrack = oldList.get(oldItemPosition);
        Track newTrack = newList.get(newItemPosition);

        // Compare all relevant fields to see if a re-bind is needed.
        // Using Objects.equals is safer for strings that might be null.
        return Objects.equals(oldTrack.title, newTrack.title)
                && Objects.equals(oldTrack.artist, newTrack.artist)
                && Objects.equals(oldTrack.album, newTrack.album)
                && Objects.equals(oldTrack.uri, newTrack.uri)
                && oldTrack.duration == newTrack.duration;
    }
}