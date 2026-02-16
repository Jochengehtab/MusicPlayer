package com.jochengehtab.musicplayer.MainActivity;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

public class MusicAnalysisViewModel extends ViewModel {
    private final MutableLiveData<String> currentStatus = new MutableLiveData<>();

    public MutableLiveData<Boolean> getIsSyncing() {
        return isSyncing;
    }

    private final MutableLiveData<Boolean> isSyncing = new MutableLiveData<>();
    private final MutableLiveData<Integer> limit = new MutableLiveData<>();

    private final MusicAnalysis musicAnalysis;

    public MusicAnalysisViewModel(MusicAnalysis musicAnalysis) {
        super();
        this.musicAnalysis = musicAnalysis;
    }

    public void startAnalysis() {
        // You call the method and provide the implementation of the callback here
        musicAnalysis.checkAndStartAnalysis(new MusicAnalysisCallback() {

            @Override
            public void onStarted() {

            }

            @Override
            public void onProgress(String trackName, int percent) {

            }

            @Override
            public void onFinish() {

            }

            @Override
            public void onUpdate(List<TaskStatus> statusList) {

            }
        });
    }

}
