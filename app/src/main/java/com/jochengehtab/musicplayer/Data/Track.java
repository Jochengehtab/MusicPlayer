package com.jochengehtab.musicplayer.Data;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "tracks", indices = {@Index(value = {"uri"}, unique = true)})
public class Track {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String uri;
    public String title;
    public String artist;
    public String album;
    public long duration;
    public long dateModified;
    public long startTime;
    public long endTime;

    // Stored as a comma-separated string (e.g., "0.123,0.552,-0.992...")
    public String embeddingVector;

    public Track(String uri, String title, String artist, String album, long duration, long dateModified) {
        this.uri = uri;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.dateModified = dateModified;
        this.startTime = 0;
        this.endTime = duration;
    }

    // Helper to convert String back to float[] for math
    public float[] getStyleVector() {
        if (embeddingVector == null || embeddingVector.isEmpty()) return null;
        try {
            String[] parts = embeddingVector.split(",");
            float[] vector = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                vector[i] = Float.parseFloat(parts[i]);
            }
            return vector;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ... (equals/hashCode remain the same)
}