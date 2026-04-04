package com.jochengehtab.musicplayer.MainActivity;

import com.jochengehtab.musicplayer.Data.Track;

import java.util.List;

public interface MusicAnalysisCallback{
    void onStarted();
    void onFinish();
    void onUpdate(List<TaskStatus> statusList, String etaString, List<String> analysisQueueTitles);
}