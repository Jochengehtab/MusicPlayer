package com.jochengehtab.musicplayer.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

// 1. CHANGE VERSION FROM 1 TO 2
@Database(entities = {Track.class, Playlist.class, PlaylistTrackCrossRef.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    // 2. DEFINE MIGRATION (Version 1 -> 2)
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add the new column 'embeddingVector' to the 'tracks' table
            database.execSQL("ALTER TABLE tracks ADD COLUMN embeddingVector TEXT");
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "music_database")
                            .addMigrations(MIGRATION_1_2) // 3. ADD MIGRATION HERE
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public abstract TrackDao trackDao();
    public abstract PlaylistDao playlistDao();
}