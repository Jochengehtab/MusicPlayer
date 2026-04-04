package com.jochengehtab.musicplayer.MainActivity;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.jochengehtab.musicplayer.AudioClassifier.AudioClassifier;
import com.jochengehtab.musicplayer.Data.AppDatabase;
import com.jochengehtab.musicplayer.Data.Track;

import java.util.List;
import java.util.concurrent.Executors;

public class MusicAnalysisViewModel extends AndroidViewModel  {
    private final MutableLiveData<Boolean> isSyncing = new MutableLiveData<>();
    private final MutableLiveData<List<TaskStatus>> activeTasks = new MutableLiveData<>();
    private final MutableLiveData<String> etaText = new MutableLiveData<>();

    private final MutableLiveData<List<String>> currentQueue = new MutableLiveData<>();
    private final MusicAnalysisModel musicAnalysisModel;

    public MusicAnalysisViewModel(@NonNull Application application) {
        super(application);
        AppDatabase database = AppDatabase.getDatabase(application);
        this.musicAnalysisModel = new MusicAnalysisModel(database, Executors.newSingleThreadExecutor(), application);
    }

    public void startAnalysis() {
        musicAnalysisModel.checkAndStartAnalysis(new MusicAnalysisCallback() {

            @Override
            public void onStarted() {
                isSyncing.postValue(true);
            }

            @Override
            public void onFinish() {
                isSyncing.postValue(false);
            }

            @Override
            public void onUpdate(List<TaskStatus> statusList, String etaString, List<String> analysisQueueTitles) {
                activeTasks.postValue(statusList);
                currentQueue.postValue(analysisQueueTitles);
                etaText.postValue(etaString);
            }
        });
    }

    public LiveData<List<TaskStatus>> getActiveTasks() { return activeTasks; }
    public LiveData<String> getEtaText() { return etaText; }
    public MutableLiveData<Boolean> getIsSyncing() {
        return isSyncing;
    }
    public MutableLiveData<List<String>> getCurrentQueue() {
        return currentQueue;
    }
}
