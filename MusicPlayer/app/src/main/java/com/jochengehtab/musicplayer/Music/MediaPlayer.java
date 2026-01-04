package com.jochengehtab.musicplayer.Music;

import com.jochengehtab.musicplayer.data.Track;

public class MediaPlayer extends android.media.MediaPlayer {

    private long startTime = 0;
    private long endTime = 0;

    private Track currentTrack = null;

    public MediaPlayer() {
        super();
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public Track getCurrentTrack() {
        return currentTrack;
    }

    public void setCurrentTrack(Track currentTrack) {
        this.currentTrack = currentTrack;
    }
}
