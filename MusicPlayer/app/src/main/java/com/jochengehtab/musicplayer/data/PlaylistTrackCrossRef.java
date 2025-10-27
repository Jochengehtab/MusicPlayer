package com.jochengehtab.musicplayer.data;

import androidx.room.Entity;
import androidx.room.Index;

@Entity(primaryKeys = {"playlistId", "trackId"},
        indices = {@Index(value = {"trackId"})})
public class PlaylistTrackCrossRef {
    public long playlistId;
    public long trackId;
}