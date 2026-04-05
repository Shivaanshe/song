package com.example.song.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.song.data.dao.SongDao
import com.example.song.data.dao.PlaylistDao
import com.example.song.data.model.Song
import com.example.song.data.model.Playlist
import com.example.song.data.model.PlaylistSongCrossRef

@Database(
    entities = [Song::class, Playlist::class, PlaylistSongCrossRef::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "song_database"
                )
                .fallbackToDestructiveMigration() // Simple for now, wipes data on schema change
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
