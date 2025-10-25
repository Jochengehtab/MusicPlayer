package com.jochengehtab.musicplayer.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
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
}