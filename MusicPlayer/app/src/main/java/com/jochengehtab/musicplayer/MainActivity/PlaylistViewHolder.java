package com.jochengehtab.musicplayer.MainActivity;

import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

public class PlaylistViewHolder extends RecyclerView.ViewHolder {
    public TextView text1;
    public PlaylistViewHolder(View itemView) {
        super(itemView);
        text1 = itemView.findViewById(android.R.id.text1);
    }
}