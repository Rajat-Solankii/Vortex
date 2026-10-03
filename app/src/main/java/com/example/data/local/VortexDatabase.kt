package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WatchHistoryEntity::class,
        BookmarkEntity::class,
        CachedHomeItem::class,
        CachedProvider::class
    ],
    version = 3,
    exportSchema = false
)
abstract class VortexDatabase : RoomDatabase() {

    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun cachedHomeItemDao(): CachedHomeItemDao
    abstract fun cachedProviderDao(): CachedProviderDao

    companion object {
        @Volatile
        private var INSTANCE: VortexDatabase? = null

        fun getInstance(context: Context): VortexDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VortexDatabase::class.java,
                    "vortex_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
