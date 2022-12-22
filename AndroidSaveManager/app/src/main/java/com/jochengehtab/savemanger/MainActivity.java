package com.jochengehtab.savemanger;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        setContentView(R.layout.activity_main);

        Button save = findViewById(R.id.save);
        Button load = findViewById(R.id.load);

        SaveManger saveManger = new SaveManger(getApplicationContext());

        save.setOnClickListener(v -> {
            saveManger.load("sdhfhpaushfash");
        });

        load.setOnClickListener(v -> {

        });
    }
}