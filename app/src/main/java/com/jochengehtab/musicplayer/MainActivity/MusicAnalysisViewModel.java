package com.jochengehtab.musicplayer.MainActivity;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.jochengehtab.musicplayer.AudioClassifier.AudioClassifier;
import com.jochengehtab.musicplayer.Data.AppDatabase;

import java.util.List;
import java.util.concurrent.Executors;

public class MusicAnalysisViewModel extends AndroidViewModel  {

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

    private final MusicAnalysisModel musicAnalysisModel;

    public MusicAnalysisViewModel(@NonNull Application application) {
        super(application);
        AppDatabase database = AppDatabase.getDatabase(application);
        AudioClassifier classifier = new AudioClassifier(application);

        this.musicAnalysisModel = new MusicAnalysisModel(database, Executors.newSingleThreadExecutor(), classifier);
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
            public void onUpdate(List<TaskStatus> statusList, String etaString) {
                activeTasks.postValue(statusList);
                etaText.postValue(etaString);
            }
        });
    }

}
