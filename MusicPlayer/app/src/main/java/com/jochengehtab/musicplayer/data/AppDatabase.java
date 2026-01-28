package com.jochengehtab.musicplayer.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

// 1. CHANGE VERSION FROM 1 TO 2
@Database(entities = {Track.class, Playlist.class, PlaylistTrackCrossRef.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "music_database")
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public abstract TrackDao trackDao();

    public abstract PlaylistDao playlistDao();
}