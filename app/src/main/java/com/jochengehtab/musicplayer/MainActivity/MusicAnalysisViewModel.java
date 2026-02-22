package com.jochengehtab.musicplayer.MainActivity;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

public class MusicAnalysisViewModel extends ViewModel {

    public MutableLiveData<Boolean> getIsSyncing() {
        return isSyncing;
    }

    private final MutableLiveData<Boolean> isSyncing = new MutableLiveData<>();

    // Holds the list
    private final MutableLiveData<List<TaskStatus>> activeTasks = new MutableLiveData<>();
    // Holds the text
    private final MutableLiveData<String> etaText = new MutableLiveData<>();

    public LiveData<List<TaskStatus>> getActiveTasks() { return activeTasks; }
    public LiveData<String> getEtaText() { return etaText; }

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
            public void onFinish() {

            }

            @Override
            public void onUpdate(List<TaskStatus> statusList, String etaString) {
                activeTasks.postValue(statusList);
                etaText.postValue(etaString);
            }
        });
    }

}
