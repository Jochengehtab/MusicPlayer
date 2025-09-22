package com.jochengehtab.musicplayer.Utility;

import static com.jochengehtab.musicplayer.MainActivity.MainActivity.ALL_TRACKS_PLAYLIST_NAME;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import com.jochengehtab.musicplayer.MusicList.Track;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class FileManager {
    private static final String PLAYLISTS_CONFIG_FILE_NAME = "playlists.json";
    private final Uri musicDirectoryUri;
    private final Context context;
    private final JSON playlistsConfig;
    private final List<Track> allTracks;

    public FileManager(Uri musicDirectoryUri, Context context, List<Track> allTracks) {
        this.context = context;
        this.musicDirectoryUri = musicDirectoryUri;

        this.allTracks = allTracks;
        DocumentFile rootDir = DocumentFile.fromTreeUri(context, musicDirectoryUri);
        if (rootDir == null) {
            throw new IllegalStateException("Cannot access the music directory. Please select it again.");
        }

        // Find or create the central playlists.json file
        DocumentFile playlistsFile = rootDir.findFile(PLAYLISTS_CONFIG_FILE_NAME);
        if (playlistsFile == null) {
            playlistsFile = rootDir.createFile("application/json", PLAYLISTS_CONFIG_FILE_NAME);
            if (playlistsFile == null) {
                throw new RuntimeException("Unable to create central playlist file: " + PLAYLISTS_CONFIG_FILE_NAME);
            }
        }
        this.playlistsConfig = new JSON(context, playlistsFile);

        if (!playlistsConfig.exists(ALL_TRACKS_PLAYLIST_NAME)) {
            createPlaylist(ALL_TRACKS_PLAYLIST_NAME);
            List<Track> all_tracks = loadMusicFiles();
            for (Track track : all_tracks) {
                addTrackToPlaylist(ALL_TRACKS_PLAYLIST_NAME, track);
            }
        }
    }

    public static String getUriHash(Uri uri) {
        return uri.toString();
    }

    /**
     * Scans the selected folder and returns a List of Tracks (uri + filename).
     */
    private ArrayList<Track> loadMusicFiles() {
        ArrayList<Track> result = new ArrayList<>();

        if (musicDirectoryUri == null) {
            return result;
        }

        DocumentFile pickedDir = DocumentFile.fromTreeUri(context, musicDirectoryUri);
        if (pickedDir == null || !pickedDir.isDirectory()) {
            return result;
        }

        for (DocumentFile file : pickedDir.listFiles()) {
            if (file.isFile()) {
                String name = file.getName();
                if (name != null && (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".m4a"))) {
                    result.add(new Track(file.getUri(), name));
                }
            }
        }
        return result;
    }
    public List<Track> loadPlaylistMusicFiles(String playlistName) {
        List<Track> result;
        if (playlistName == null) {
            result = allTracks;
        } else {
            result = playlistsConfig.readList(playlistName, Track.class);
        }

        return result;
    }

    /**
     * Reads the list of playlist names from the central playlists.json config file.
     * This is very fast as it doesn't scan the file system.
     *
     * @return A list of playlist names.
     */
    public List<String> listPlaylists() {
        try {
            Set<String> keys = playlistsConfig.getKeys();

            // Remove current_playlist
            keys.remove("current_playlist");

            // Convert to an Arraylist
            return new ArrayList<>(keys);
        } catch (RuntimeException e) {
            Log.e("FileManager", "Error listing playlists", e);
            Toast.makeText(context, "Could not load playlists.", Toast.LENGTH_SHORT).show();
            return new ArrayList<>();
        }
    }

    /**
     * Creates a new playlist by adding an entry to the central playlists.json config file.
     *
     * @param name The name for the new playlist.
     * @return true if successful, false otherwise.
     */
    public boolean createPlaylist(String name) {
        if (name == null || name.trim().isEmpty()) {
            Toast.makeText(context, "Playlist name cannot be empty.", Toast.LENGTH_SHORT).show();
            return false;
        }

        try {
            List<String> existingPlaylists = listPlaylists();
            if (existingPlaylists.stream().anyMatch(p -> p.equalsIgnoreCase(name))) {
                Toast.makeText(context, "A playlist with that name already exists.", Toast.LENGTH_SHORT).show();
                return false;
            }
            // Create a new entry with an empty list of tracks
            playlistsConfig.write(name, new ArrayList<Track>());
            return true;
        } catch (RuntimeException e) {
            Log.e("FileManager", "Error creating playlist", e);
            Toast.makeText(context, "Failed to create playlist.", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * Reads the track list for a given playlist from the central playlists.json file
     * and then applies the specified sorting order.
     *
     * @param playlistName The name of the playlist to load.
     * @param sortOrder    The sorting order to apply ("A-Z" or "Date").
     * @return A list of tracks from the playlist, sorted as requested.
     */
    public List<Track> loadTracksFromPlaylist(String playlistName, String sortOrder) {

        assert playlistName != null;

        // For any playlist, always read the list from the JSON file first
        List<Track> playlistTracks = playlistsConfig.readList(playlistName, Track.class);

        // The playlist doesn't exist in the JSON file
        if (!playlistsConfig.exists(playlistName)) {
            createPlaylist(playlistName);
            return new ArrayList<>();
        }

        // Now, apply the requested sorting to the list that was loaded from the JSON
        if ("A-Z".equals(sortOrder)) {
            playlistTracks.sort((t1, t2) -> {
                boolean t1IsDigit = Character.isDigit(t1.title().charAt(0));
                boolean t2IsDigit = Character.isDigit(t2.title().charAt(0));

                if (t1IsDigit && !t2IsDigit) {
                    return 1; // Numbers come after letters
                } else if (!t1IsDigit && t2IsDigit) {
                    return -1; // Letters come before numbers
                } else {
                    // Both are letters or both are numbers, sort alphabetically
                    return t1.title().compareToIgnoreCase(t2.title());
                }
            });
        } else if ("Date".equals(sortOrder)) {
            playlistTracks.sort(Comparator.comparingLong((Track t) -> {
                DocumentFile file = DocumentFile.fromSingleUri(context, t.uri());
                return file != null ? file.lastModified() : 0;
            }).reversed());
        }

        return playlistTracks;
    }

    /**
     * Deletes a playlist entry from the central playlists.json file.
     *
     * @param playlistName The name of the playlist to delete.
     * @return true if successful, false otherwise.
     */
    public boolean deletePlaylist(String playlistName) {
        try {
            playlistsConfig.remove(playlistName);
            Toast.makeText(context, "Playlist '" + playlistName + "' deleted.", Toast.LENGTH_SHORT).show();
            return true;
        } catch (RuntimeException e) {
            Log.e("FileManager", "Error deleting playlist", e);
            Toast.makeText(context, "Failed to delete playlist.", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * Adds a track to a specified playlist's entry in the central JSON file.
     *
     * @param playlistName The name of the playlist.
     * @param track        The track to add.
     */
    public void addTrackToPlaylist(String playlistName, Track track) {
        try {
            // Read the existing list of tracks for the given playlist
            List<Track> playlistTracks = playlistsConfig.readList(playlistName, Track.class);
            if (playlistTracks == null) {
                // This case shouldn't happen if playlists are created properly
                playlistTracks = new ArrayList<>();
            }

            // Check if track is already in the playlist
            boolean alreadyExists = playlistTracks.stream().anyMatch(t -> t.uri().equals(track.uri()));
            if (alreadyExists) {
                Toast.makeText(context, "Track is already in this playlist.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Add the new track and write the updated list back under the same key
            playlistTracks.add(track);
            playlistsConfig.write(playlistName, playlistTracks);

            Toast.makeText(context, "Added '" + track.title() + "' to " + playlistName, Toast.LENGTH_SHORT).show();

        } catch (RuntimeException e) {
            Log.e("PlaylistError", "Error updating playlist. See stack trace.", e);
            Toast.makeText(context, "Error updating playlist: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public String getCurrentPlaylistName() {
        String current_playlist = playlistsConfig.read("current_playlist", String.class);
        if (current_playlist == null) {
            playlistsConfig.write("current_playlist", ALL_TRACKS_PLAYLIST_NAME);
            return ALL_TRACKS_PLAYLIST_NAME;
        }
        return current_playlist;
    }

    public void setCurrentPlaylistName(String name) {
        assert name != null;
        playlistsConfig.write("current_playlist", name);
    }
}