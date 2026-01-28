package com.jochengehtab.musicplayer.MusicList;

import com.jochengehtab.musicplayer.data.Track;

/**
 * A simple interface to handle click events on a Track item in the RecyclerView.
 */
public interface OnItemClickListener {
    /**
     * Called when a track in the list is clicked.
     *
     * @param track The Track entity object that was clicked.
     */
    void onItemClick(Track track);
}