package com.jochengehtab.musicplayer.MusicList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jochengehtab.musicplayer.MainActivity.MainActivity;
import com.jochengehtab.musicplayer.R;

import java.util.List;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistViewHolder> {

    private final Context context;
    private final List<String> playlists;
    private final PlaylistActionsListener listener;

    public PlaylistAdapter(Context context, List<String> playlists, PlaylistActionsListener listener) {
        this.context = context;
        this.playlists = playlists;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_playlist, parent, false);
        return new PlaylistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        String playlistName = playlists.get(position);
        holder.playlistNameText.setText(playlistName);

        holder.itemView.setOnClickListener(v -> listener.onSelectClicked(playlistName));

        holder.optionsButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(context, holder.optionsButton);
            popup.inflate(R.menu.playlist_item_menu);

            if (playlistName.equals(MainActivity.ALL_TRACKS_PLAYLIST_NAME)) {
                popup.getMenu().findItem(R.id.action_delete_playlist).setVisible(false);
            }

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_play_playlist) {
                    listener.onPlayClicked(playlistName);
                    return true;
                } else if (id == R.id.action_delete_playlist) {
                    listener.onDeleteClicked(playlistName);
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }
}