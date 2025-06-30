package com.jochengehtab.musicplayer.MusicList.Options;

import static com.jochengehtab.musicplayer.MainActivity.MainActivity.timestampsConfig;

import com.jochengehtab.musicplayer.MusicList.Track;
import com.jochengehtab.musicplayer.Utility.FileManager;

public class Reset {

    public Reset() {
    }

    public void reset(Track track) {
        Integer[] timestamps = timestampsConfig.readArray(
                FileManager.getUriHash(track.uri()), Integer[].class
        );

        assert timestamps.length > 2;

        timestampsConfig.write(
                FileManager.getUriHash(track.uri()),
                new int[]{0, timestamps[2], timestamps[2]}
        );
    }
}
