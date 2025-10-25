package com.jochengehtab.musicplayer.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertPlaylist(Playlist playlist);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertPlaylistTrackCrossRef(PlaylistTrackCrossRef crossRef);

    @Transaction
    @Query("SELECT * FROM playlists WHERE name = :playlistName")
    PlaylistWithTracks getPlaylistWithTracks(String playlistName);

    @Transaction
    @Query("SELECT * FROM playlists")
    List<PlaylistWithTracks> getAllPlaylistsWithTracks();

    @Query("DELETE FROM playlists WHERE name = :playlistName")
    void deletePlaylist(String playlistName);

    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    Playlist getPlaylistByName(String name);

    @Query("SELECT name FROM playlists")
    List<String> getAllPlaylistNames();

    @Query("DELETE FROM PlaylistTrackCrossRef WHERE playlistId = (SELECT id FROM playlists WHERE name = :playlistName) AND trackId = :trackId")
    void removeTrackFromPlaylist(String playlistName, long trackId);
}