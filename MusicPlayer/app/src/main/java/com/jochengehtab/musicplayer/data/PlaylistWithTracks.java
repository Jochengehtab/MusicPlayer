package com.jochengehtab.musicplayer.data;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;

import java.util.List;

public class PlaylistWithTracks {
    @Embedded
    public Playlist playlist;

    @Relation(
            parentColumn = "id",
            entityColumn = "id",
            associateBy = @Junction(
                    value = PlaylistTrackCrossRef.class,
                    parentColumn = "playlistId",
                    entityColumn = "trackId"
            )
    )
    public List<Track> tracks;
}