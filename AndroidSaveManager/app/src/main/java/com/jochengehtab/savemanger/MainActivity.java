package com.jochengehtab.savemanger;

import android.content.pm.ActivityInfo;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        setContentView(R.layout.activity_main);

        MusicPlayer musicPlayer = new MusicPlayer(getApplicationContext(), R.raw.alte_kameraden);

        Button load = findViewById(R.id.load);

        load.setOnClickListener(v -> {
            musicPlayer.playMusic();
        });
    }
}