package com.jochengehtab.musicplayer.AudioClassifier;

import androidx.annotation.NonNull;

// A class to hold a consolidated event with a start and end time.
public class Event {
    public final String label;
    public final float start;
    public float end; // This can be modified, so not final

    public Event(String label, float start, float end) {
        this.label = label;
        this.start = start;
        this.end = end;
    }

    @NonNull
    @Override
    public String toString() {
        return "Event{" +
                "label='" + label + '\'' +
                ", start=" + start +
                ", end=" + end +
                '}';
    }
}