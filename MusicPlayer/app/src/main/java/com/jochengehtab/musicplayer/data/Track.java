package com.jochengehtab.musicplayer.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tracks")
public class Track {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String uri;
    public String title;
    public String artist;
    public String album;
    public long duration;

    public Track(String uri, String title, String artist, String album, long duration) {
        this.uri = uri;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
    }
}