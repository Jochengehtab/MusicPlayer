package com.jochengehtab.musicplayer.MusicList;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jochengehtab.musicplayer.R;

public class TrackViewHolder extends RecyclerView.ViewHolder {
    final TextView titleText;
    final ImageView overflowIcon;

    public TrackViewHolder(@NonNull View itemView) {
        super(itemView);
        titleText = itemView.findViewById(R.id.text_track_title);
        overflowIcon = itemView.findViewById(R.id.overflow_icon);
    }
}