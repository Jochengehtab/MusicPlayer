package com.jochengehtab.musicplayer.Data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void syncTracks(List<Track> tracks);

    @Query("SELECT * FROM tracks")
    List<Track> getAllTracks();

    @Query("SELECT * FROM tracks WHERE id = :trackId")
    Track getTrack(long trackId);

    @Update
    void updateTrack(Track track);

    @Query("UPDATE tracks SET embeddingVector = :vector WHERE id = :trackId")
    void updateTrackEmbedding(long trackId, String vector);

    @Query("DELETE FROM tracks WHERE uri NOT IN (:currentUris)")
    void deleteOrphanedTracks(List<String> currentUris);
}