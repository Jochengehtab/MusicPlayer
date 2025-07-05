package com.jochengehtab.musicplayer.Utility;

import static com.jochengehtab.musicplayer.MainActivity.MainActivity.timestampsConfig;

import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import com.jochengehtab.musicplayer.Music.MusicUtility;
import com.jochengehtab.musicplayer.MusicList.Track;

import java.util.ArrayList;
import java.util.List;

public class FileManager {
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
                    if (timestampsConfig.read(getUriHash(file.getUri()), Integer[].class) == null) {
                        final int duration = musicUtility.getTrackDuration(file.getUri());
                        int[] timestamps = {0, duration, duration};
                        timestampsConfig.write(getUriHash(file.getUri()), timestamps);
                    }
                    result.add(new Track(file.getUri(), name));
                }
            }
        }
        return result;
    }

    /**
     * Lists all subdirectories within the main music directory.
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
}