package com.jochengehtab.musicplayer.MusicList;

public interface PlaylistActionsListener {
    void onPlayClicked(String playlistName);

    void onDeleteClicked(String playlistName);

    void onSelectClicked(String playlistName);
}