package com.jochengehtab.musicplayer.MainActivity;

public class TaskStatus {
    String trackTitle;
    int progress;
    long startTime;

    TaskStatus(String title, long start) {
        this.trackTitle = title;
        this.startTime = start;
        this.progress = 0;
    }
}