package com.jochengehtab.musicplayer.Music;

/**
 * Signals when playback actually starts and when it finally stops.
 */
public interface OnPlaybackStateListener {
    void onPlaybackStarted();

    void onPlaybackStopped();
}