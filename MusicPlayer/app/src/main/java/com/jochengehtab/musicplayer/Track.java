package com.jochengehtab.musicplayer;


import android.net.Uri;

public class Track {
    private final Uri uri;
    private final String title;

    public Track(Uri uri, String title) {
        this.uri = uri;
        this.title = title;
    }

    public Uri getUri() {
        return uri;
    }

    public String getTitle() {
        return title;
    }
}