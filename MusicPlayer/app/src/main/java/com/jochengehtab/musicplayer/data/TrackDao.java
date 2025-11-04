package com.jochengehtab.musicplayer.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update; // Import Update

import java.util.List;

@Dao
public interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Track> tracks);

    @Query("SELECT * FROM tracks")
    List<Track> getAllTracks();

    @Update // Add this method
    void update(Track track);

    @Query("SELECT COUNT(*) FROM tracks")
    int getTrackCount();

    @Query("DELETE FROM PlaylistTrackCrossRef")
    void clearPlaylistJoins();

    @Query("DELETE FROM playlists")
    void clearPlaylists();

    @Query("DELETE FROM tracks")
    void clearAllTracks();

    /**
     * This completely wipes all
     * music-related data and replaces it with a fresh list.
     * This is the simplest way to handle additions, deletions, and changes.
     */
    @Transaction
    default void fullResync(List<Track> newTracks) {
        // 1. Wipe all existing data in the correct order to respect foreign keys.
        clearPlaylistJoins(); // Clear the link table first.
        clearPlaylists();     // Then clear the playlists table.
        clearAllTracks();     // Finally, clear the main tracks table.

        // 2. Insert the fresh list of tracks from MediaStore.
        insertAll(newTracks);
    }
}