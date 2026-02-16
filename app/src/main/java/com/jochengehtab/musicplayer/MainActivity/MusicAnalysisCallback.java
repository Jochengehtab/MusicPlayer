package com.jochengehtab.musicplayer.MainActivity;

import java.util.List;

public interface MusicAnalysisCallback{
    void onStarted();
    void onProgress(String trackName, int percent);
    void onFinish();
    void onUpdate(List<TaskStatus> statusList);
}