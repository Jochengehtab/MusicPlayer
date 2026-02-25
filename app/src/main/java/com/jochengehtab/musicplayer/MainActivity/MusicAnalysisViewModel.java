package com.jochengehtab.musicplayer.MainActivity;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

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

    private final MusicAnalysis musicAnalysis;

    public MusicAnalysisViewModel(@NonNull Application application) {
        super(application);
        AppDatabase database = AppDatabase.getDatabase(application);
        AudioClassifier classifier = new AudioClassifier(application);

        this.musicAnalysis = new MusicAnalysis(database, Executors.newSingleThreadExecutor(), classifier);
    }

    public void startAnalysis() {
        Log.i("Started", "Schtart1");
        // You call the method and provide the implementation of the callback here
        musicAnalysis.checkAndStartAnalysis(new MusicAnalysisCallback() {

            @Override
            public void onStarted() {
                Log.i("Started", "Schtart");
            }

            @Override
            public void onFinish() {
                Log.i("Finish", "Finish");
            }

            @Override
            public void onUpdate(List<TaskStatus> statusList, String etaString) {
                activeTasks.postValue(statusList);
                etaText.postValue(etaString);
                Log.i("Update", "Update");
            }
        });
    }

}
