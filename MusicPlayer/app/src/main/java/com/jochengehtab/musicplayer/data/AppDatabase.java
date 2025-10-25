package com.jochengehtab.musicplayer.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

// Increment the version number from 1 to 2
@Database(entities = {Track.class, Playlist.class, PlaylistTrackCrossRef.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract TrackDao trackDao();
    public abstract PlaylistDao playlistDao();

    private static volatile AppDatabase INSTANCE;

    // Define the migration from version 1 to 2
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add the new columns to the existing 'tracks' table
            database.execSQL("ALTER TABLE tracks ADD COLUMN startTime INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE tracks ADD COLUMN endTime INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "music_database")
                            // Add the migration path here
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}