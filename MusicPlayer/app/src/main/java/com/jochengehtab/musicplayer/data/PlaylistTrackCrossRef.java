package com.jochengehtab.musicplayer.data;

import androidx.room.Entity;

@Entity(primaryKeys = {"playlistId", "trackId"})
public class PlaylistTrackCrossRef {
    public long playlistId;
    public long trackId;
}