package com.jochengehtab.musicplayer.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface PlaylistDao {

    /**
     * Creates a new empty playlist.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long createPlaylist(Playlist playlist);

    /**
     * Adds a single track to a playlist (Insert single CrossRef).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void addTrackToPlaylist(PlaylistTrackCrossRef crossRef);

    /**
     * Adds multiple tracks to a playlist at once (Insert list of CrossRefs).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void addTracksToPlaylist(List<PlaylistTrackCrossRef> crossRefs);

    /**
     * Returns a specific playlist entity (metadata only, no tracks) by name.
     */
    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    Playlist getPlaylist(String name);

    /**
     * Returns the Playlist object AND its list of Tracks.
     * Requires @Transaction because it queries two tables.
     */
    @Transaction
    @Query("SELECT * FROM playlists WHERE name = :playlistName")
    PlaylistWithTracks getPlaylistWithTracks(String playlistName);

    /**
     * Returns a list of all playlist names to display in the UI.
     */
    @Query("SELECT name FROM playlists")
    List<String> getAllPlaylistNames();

    /**
     * Removes a specific track from a specific playlist.
     */
    @Query("DELETE FROM PlaylistTrackCrossRef WHERE playlistId = (SELECT id FROM playlists WHERE name = :playlistName) AND trackId = :trackId")
    void removeTrackFromPlaylist(String playlistName, long trackId);

    /**
     * Removes ALL tracks from a playlist, but keeps the empty playlist exists.
     */
    @Query("DELETE FROM PlaylistTrackCrossRef WHERE playlistId = :playlistId")
    void removeAllTracksFromPlaylist(long playlistId);

    /**
     * Completely deletes the playlist entity.
     * (Room will handle the CrossRefs automatically if Cascading is set,
     * otherwise this leaves orphan records in CrossRef, but usually fine for simple apps).
     */
    @Query("DELETE FROM playlists WHERE name = :playlistName")
    void deletePlaylist(String playlistName);
}