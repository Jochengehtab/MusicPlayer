package com.jochengehtab.musicplayer.Utility;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.jochengehtab.musicplayer.MusicList.Track;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

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
                if (name != null && (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".m4a"))) {
                    int durationMs;
                    // Determine track duration in seconds
                    try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
                        retriever.setDataSource(context, file.getUri());
                        String durationStr = retriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_DURATION);
                        durationMs = Integer.parseInt(Objects.requireNonNull(durationStr));
                        try {
                            retriever.release();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    final int durationSec = durationMs / 1000;
                    result.add(new Track(file.getUri(), name));
                }
            }
        }
        return result;
    }
}