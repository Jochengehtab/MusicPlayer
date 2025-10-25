package com.jochengehtab.musicplayer.data;

import androidx.room.Entity;
import androidx.room.Index; // <-- Import Index

// Add the indices property to the @Entity annotation
@Entity(primaryKeys = {"playlistId", "trackId"},
        indices = {@Index(value = {"trackId"})}) // <-- THIS IS THE FIX
public class PlaylistTrackCrossRef {
    public long playlistId;
    public long trackId;
}