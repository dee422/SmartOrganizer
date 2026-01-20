package com.dee.android.pbl.smartorganizer

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Container::class, StorageItem::class], version = 2, exportSchema = false) // 💡 版本升到 2
abstract class AppDatabase : RoomDatabase() {
    abstract fun containerDao(): ContainerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_organizer_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}