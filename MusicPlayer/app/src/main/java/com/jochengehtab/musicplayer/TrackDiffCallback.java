package com.jochengehtab.musicplayer;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import java.util.List;

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
     * Two Tracks are considered the same if they point to the same URI.
     */
    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        return oldList.get(oldItemPosition).uri()
                .equals(newList.get(newItemPosition).uri());
    }

    /**
     * Contents are the same if title (and uri) are unchanged. If you add more fields
     * to Track, compare them here too.
     */
    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        Track oldTrack = oldList.get(oldItemPosition);
        Track newTrack = newList.get(newItemPosition);
        return oldTrack.title().equals(newTrack.title())
                && oldTrack.uri().equals(newTrack.uri());
    }
}
