package com.jochengehtab.musicplayer.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TrackDao {
    /**
     * Syncs MediaStore tracks into the local database.
     * <p>
     * Use OnConflictStrategy.IGNORE:
     * 1. If the track is new, it gets inserted.
     * 2. If the track is already there (and maybe has custom trim times),
     * it is LEFT ALONE. We don't want to overwrite user data.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void syncTracks(List<Track> tracks);

    @Query("SELECT * FROM tracks")
    List<Track> getAllTracks();

    @Query("SELECT * FROM tracks WHERE id = :trackId")
    Track getTrack(long trackId);

    /**
     * Save changes when the user edits the Start/End trim times.
     */
    @Update
    void updateTrack(Track track);

    @Query("DELETE FROM tracks WHERE uri NOT IN (:currentUris)")
    void deleteOrphanedTracks(List<String> currentUris);
}