package com.jochengehtab.musicplayer.data;

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
    public long duration; // This is the original, full duration in ms
    public long dateModified;

    // New fields for trimming
    public long startTime; // Start trim position in ms
    public long endTime;   // End trim position in ms

    // Make sure to have a constructor that Room can use
    public Track(String uri, String title, String artist, String album, long duration, long dateModified) {
        this.uri = uri;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.dateModified = dateModified;
        // By default, a track is not trimmed
        this.startTime = 0;
        this.endTime = duration;
    }
}