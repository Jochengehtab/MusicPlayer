package com.jochengehtab.musicplayer.MusicList.Options;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog.Builder;

import com.jochengehtab.musicplayer.Music.MusicUtility;
import com.jochengehtab.musicplayer.R;
import com.jochengehtab.musicplayer.Data.AppDatabase;
import com.jochengehtab.musicplayer.Data.Track;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Trim {
    private final Context context;
    private final MusicUtility musicUtility;
    private final AppDatabase database;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public Trim(Context context, MusicUtility musicUtility, AppDatabase database) {
        this.context = context;
        this.musicUtility = musicUtility;
        this.database = database;
    }

    public void showTrimDialog(Track track) {
        Builder builder = new Builder(context);
        builder.setTitle("Trim Track");

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_trim, null);
        builder.setView(dialogView);

        SeekBar seekStart = dialogView.findViewById(R.id.seek_start);
        SeekBar seekEnd = dialogView.findViewById(R.id.seek_end);
        TextView valueStart = dialogView.findViewById(R.id.value_start);
        TextView valueEnd = dialogView.findViewById(R.id.value_end);
        Button buttonPreview = dialogView.findViewById(R.id.button_preview);

        final long durationMs = track.duration;
        long startMs = track.startTime;
        long endMs = (track.endTime <= 0 || track.endTime > durationMs) ? durationMs : track.endTime;

        seekStart.setMax((int) durationMs);
        seekEnd.setMax((int) durationMs);
        seekStart.setProgress((int) startMs);
        seekEnd.setProgress((int) endMs);
        valueStart.setText(String.valueOf(startMs / 1000));
        valueEnd.setText(String.valueOf(endMs / 1000));

        seekStart.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (progress >= seekEnd.getProgress()) {
                    progress = Math.max(0, seekEnd.getProgress() - 1000);
                    sb.setProgress(progress);
                }
                valueStart.setText(String.valueOf(progress / 1000));
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });

        seekEnd.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (progress <= seekStart.getProgress()) {
                    progress = Math.min((int) durationMs, seekStart.getProgress() + 1000);
                    sb.setProgress(progress);
                }
                valueEnd.setText(String.valueOf(progress / 1000));
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });

        buttonPreview.setOnClickListener(v -> {
            long start = seekStart.getProgress();
            long end = seekEnd.getProgress();
            // Pass the entire track object for the preview.
            // The timespan argument overrides the saved trim times.
            musicUtility.playTrack(track, start, end);
        });

        builder.setPositiveButton("OK", (dialog, which) -> {
            track.startTime = seekStart.getProgress();
            track.endTime = seekEnd.getProgress();

            executor.execute(() -> {
                database.trackDao().updateTrack(track);
                handler.post(() -> Toast.makeText(context, "Trim saved", Toast.LENGTH_SHORT).show());
            });
            dialog.dismiss();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.create().show();
    }
}