package com.jochengehtab.musicplayer.Utility;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import com.jochengehtab.musicplayer.Music.MusicUtility;
import com.jochengehtab.musicplayer.MusicList.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FileManager {
    private static final String PLAYLIST_FILE_NAME = "playlist.json";
    private static final String TRACKS_KEY = "tracks";
    private final Uri musicDirectoryUri;
    private final Context context;
    private final MusicUtility musicUtility;

    public FileManager(Uri musicDirectoryUri, Context context, MusicUtility musicUtility) {
        this.musicDirectoryUri = musicDirectoryUri;
        this.context = context;
        this.musicUtility = musicUtility;
    }

    public static String getUriHash(Uri uri) {
        return uri.toString();
    }

    /**
     * Scans the selected folder and returns a List of Tracks (uri + filename).
     */
    public ArrayList<Track> loadMusicFiles() {
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

    /**
     * Lists all subdirectories within the main music directory.
     *
     * @return A list of folder names.
     */
    public List<String> listFolders() {
        List<String> folders = new ArrayList<>();
        DocumentFile rootDir = DocumentFile.fromTreeUri(context, musicDirectoryUri);
        if (rootDir != null && rootDir.isDirectory()) {
            for (DocumentFile file : rootDir.listFiles()) {
                if (file.isDirectory()) {
                    folders.add(file.getName());
                }
            }
        }
        return folders;
    }

    /**
     * Creates a new subfolder in the main music directory.
     *
     * @param name The name for the new folder.
     * @return true if successful, false otherwise.
     */
    public boolean createFolder(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        DocumentFile rootDir = DocumentFile.fromTreeUri(context, musicDirectoryUri);
        if (rootDir != null && rootDir.isDirectory()) {
            // Check if a folder with that name already exists
            if (rootDir.findFile(name) != null) {
                Toast.makeText(context, "A playlist with that name already exists.", Toast.LENGTH_SHORT).show();
                return false;
            }
            DocumentFile newDir = rootDir.createDirectory(name);
            return newDir != null && newDir.exists();
        }
        return false;
    }

    /**
     * Reads the playlist.json file from a given playlist folder.
     *
     * @param playlistName The name of the folder representing the playlist.
     * @return A list of tracks from the playlist, or an empty list if not found or empty.
     */
    public List<Track> loadTracksFromPlaylist(String playlistName) {
        DocumentFile rootDir = DocumentFile.fromTreeUri(context, musicDirectoryUri);
        if (rootDir == null) return new ArrayList<>();

        DocumentFile playlistDir = rootDir.findFile(playlistName);
        if (playlistDir == null || !playlistDir.isDirectory()) {
            Toast.makeText(context, "Playlist '" + playlistName + "' not found.", Toast.LENGTH_SHORT).show();
            return new ArrayList<>();
        }

        DocumentFile playlistFile = playlistDir.findFile(PLAYLIST_FILE_NAME);
        if (playlistFile == null) {
            return new ArrayList<>(); // Playlist folder exists but has no songs yet.
        }

        try {
            JSON playlistJson = new JSON(context, playlistFile);
            List<Track> playlistTracks = playlistJson.readList(TRACKS_KEY, Track.class);
            return Objects.requireNonNullElseGet(playlistTracks, ArrayList::new);
        } catch (RuntimeException e) {
            Toast.makeText(context, "Error reading playlist: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.i("e", "Error reading playlist: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Deletes a playlist folder and all its contents.
     *
     * @param playlistName The name of the playlist to delete.
     * @return true if successful, false otherwise.
     */
    public boolean deletePlaylist(String playlistName) {
        DocumentFile rootDir = DocumentFile.fromTreeUri(context, musicDirectoryUri);
        if (rootDir == null) return false;

        DocumentFile playlistDir = rootDir.findFile(playlistName);
        if (playlistDir != null && playlistDir.isDirectory()) {
            // DocumentFile.delete() for a directory should delete its contents as well.
            if (playlistDir.delete()) {
                Toast.makeText(context, "Playlist '" + playlistName + "' deleted.", Toast.LENGTH_SHORT).show();
                return true;
            } else {
                Toast.makeText(context, "Failed to delete playlist.", Toast.LENGTH_SHORT).show();
                return false;
            }
        } else {
            Toast.makeText(context, "Playlist not found.", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * Adds a track to a specified playlist's JSON file using the JSON utility.
     *
     * @param playlistName The name of the folder representing the playlist.
     * @param track        The track to add.
     */
    public void addTrackToPlaylist(String playlistName, Track track) {
        DocumentFile rootDir = DocumentFile.fromTreeUri(context, musicDirectoryUri);
        if (rootDir == null) return;

        // 1. Find the playlist directory
        DocumentFile playlistDir = rootDir.findFile(playlistName);
        if (playlistDir == null || !playlistDir.isDirectory()) {
            Toast.makeText(context, "Playlist folder not found!", Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. Find or create the playlist.json file
        DocumentFile playlistFile = playlistDir.findFile(PLAYLIST_FILE_NAME);
        if (playlistFile == null) {
            playlistFile = playlistDir.createFile("application/json", PLAYLIST_FILE_NAME);
            if (playlistFile == null) {
                Toast.makeText(context, "Failed to create playlist file!", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Log.i("Name", track.title());
        Log.i("URI", String.valueOf(track.uri()));

        // 3. Use the JSON utility to read, update, and write
        try {
            JSON playlistJson = new JSON(context, playlistFile);

            // Read the existing list of tracks
            List<Track> playlistTracks = playlistJson.readList(TRACKS_KEY, Track.class);
            if (playlistTracks == null) {
                playlistTracks = new ArrayList<>(); // If key doesn't exist, start a new list
            }

            // Check if track is already in the playlist
            boolean alreadyExists = playlistTracks.stream().anyMatch(t -> t.uri().equals(track.uri()));
            if (alreadyExists) {
                Toast.makeText(context, "Track is already in this playlist.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Add the new track and write the entire list back
            playlistTracks.add(track);
            playlistJson.write(TRACKS_KEY, playlistTracks);

            Toast.makeText(context, "Added '" + track.title() + "' to " + playlistName, Toast.LENGTH_SHORT).show();

        } catch (RuntimeException e) {
            Log.e("PlaylistError", "Error updating playlist. See stack trace.", e);
            Toast.makeText(context, "Error updating playlist: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}