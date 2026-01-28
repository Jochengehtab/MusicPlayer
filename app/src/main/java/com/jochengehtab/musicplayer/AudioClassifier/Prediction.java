package com.jochengehtab.musicplayer.AudioClassifier;

// A simple class to hold a single prediction at a specific time.
public class Prediction {
    public final float time;
    public final String label;

    public Prediction(float time, String label) {
        this.time = time;
        this.label = label;
    }
}