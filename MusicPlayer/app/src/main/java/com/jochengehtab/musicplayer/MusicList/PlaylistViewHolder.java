package com.jochengehtab.musicplayer.MusicList;

import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jochengehtab.musicplayer.R;

public class PlaylistViewHolder extends RecyclerView.ViewHolder {
    TextView playlistNameText;
    ImageButton optionsButton;

    public PlaylistViewHolder(@NonNull View itemView) {
        super(itemView);
        playlistNameText = itemView.findViewById(R.id.playlist_name);
        optionsButton = itemView.findViewById(R.id.playlist_options);
    }
}
