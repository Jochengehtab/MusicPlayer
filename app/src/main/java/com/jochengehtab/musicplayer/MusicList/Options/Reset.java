package com.jochengehtab.musicplayer.MusicList.Options;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.jochengehtab.musicplayer.Data.AppDatabase;
import com.jochengehtab.musicplayer.Data.Track;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Reset {
    private final AppDatabase database;
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public Reset(Context context, AppDatabase database) {
        this.context = context;
        this.database = database;
    }

    /**
     * Resets the trim times for a track and updates the database.
     */
    public void reset(Track track) {
        // Reset start and end times to the full duration
        track.startTime = 0;
        track.endTime = track.duration;

        // Update the track in the database on a background thread
        executor.execute(() -> {
            database.trackDao().updateTrack(track);
            handler.post(() -> Toast.makeText(context, "Track trim has been reset", Toast.LENGTH_SHORT).show());
        });
    }
}