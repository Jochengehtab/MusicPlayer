package com.jochengehtab.musicplayer;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;

public class FileManager {
    private final Uri musicDirectoryUri;
    private final Context context;

    public FileManager(Uri musicDirectoryUri, Context context) {
        this.musicDirectoryUri = musicDirectoryUri;
        this.context = context;
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
                if (name != null && (name.endsWith(".mp3") || name.endsWith(".wav"))) {
                    result.add(new Track(file.getUri(), name));
                }
            }
        }
        return result;
    }
}