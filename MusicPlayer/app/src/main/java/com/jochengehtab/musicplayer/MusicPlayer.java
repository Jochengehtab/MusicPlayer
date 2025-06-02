package com.jochengehtab.musicplayer;

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

    public void playMix(ArrayList<Track> musicFiles) {
        ArrayList<Track> musicFilesCopy = (ArrayList<Track>) musicFiles.clone();

        new Thread(() -> {
            while (!musicFilesCopy.isEmpty()) {
                if (canResume) {
                    int index = random.nextInt(musicFilesCopy.size());

                    musicUtility.play(musicFilesCopy.get(index).getUri());

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
