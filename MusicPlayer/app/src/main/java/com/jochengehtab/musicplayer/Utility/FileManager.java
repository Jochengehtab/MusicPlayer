package com.jochengehtab.musicplayer.Utility;

import static com.jochengehtab.musicplayer.MainActivity.MainActivity.timestampsConfig;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.jochengehtab.musicplayer.Music.MusicUtility;
import com.jochengehtab.musicplayer.MusicList.Track;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

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
        return UUID.nameUUIDFromBytes(uri.toString().getBytes(StandardCharsets.UTF_8)).toString();
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
                        int[] timestamps = {0, musicUtility.getTrackDuration(file.getUri())};
                        timestampsConfig.write(getUriHash(file.getUri()), timestamps);
                    }
                    result.add(new Track(file.getUri(), name));
                }
            }
        }
        return result;
    }
}