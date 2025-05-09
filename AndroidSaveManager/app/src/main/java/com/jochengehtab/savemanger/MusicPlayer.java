package com.jochengehtab.savemanger;

import android.net.Uri;

import java.util.ArrayList;
import java.util.Random;

public class MusicPlayer {
    private final MusicUtility musicUtility;
    private final Random random = new Random();
    public static boolean canResume = true;

    public MusicPlayer(MusicUtility musicUtility) {
        this.musicUtility = musicUtility;
    }

    public void playMix(ArrayList<Uri> musicFiles) {
        ArrayList<Uri> musicFilesCopy = (ArrayList<Uri>) musicFiles.clone();

        new Thread(() -> {
            while (!musicFilesCopy.isEmpty()) {
                if (canResume) {
                    int index = random.nextInt(musicFilesCopy.size());

                    musicUtility.play(musicFilesCopy.get(index));

                    musicFilesCopy.remove(index);
                }
                else {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }).start();
    }
}
