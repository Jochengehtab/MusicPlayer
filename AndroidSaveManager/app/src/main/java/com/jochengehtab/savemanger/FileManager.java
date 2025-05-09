package com.jochengehtab.savemanger;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;

public class FileManager {

    private final ArrayList<Uri> trackUris = new ArrayList<>();
    private final ArrayList<String> titles = new ArrayList<>();
    private Uri musicDirectoryUri;
    private Context context;

    public FileManager(Uri musicDirectoryUri, Context context) {
        this.musicDirectoryUri = musicDirectoryUri;
        this.context = context;
    }

    public void loadMusicFiles() {

        // Check if we have at least one selected folder
        if (musicDirectoryUri == null) {
            return;
        }

        DocumentFile pickedDir = DocumentFile.fromTreeUri(context, musicDirectoryUri);
        if (pickedDir == null || !pickedDir.isDirectory()) {
            return;
        }

        for (DocumentFile file : pickedDir.listFiles()) {
            if (file.isFile()) {
                String name = file.getName();
                if (name != null && (name.endsWith(".mp3") || name.endsWith(".wav"))) {
                    trackUris.add(file.getUri());
                    titles.add(name);
                }
            }
        }
    }

    public ArrayList<Uri> getTrackUris() {
        return trackUris;
    }

    public ArrayList<String> getTitles() {
        return titles;
    }

}
