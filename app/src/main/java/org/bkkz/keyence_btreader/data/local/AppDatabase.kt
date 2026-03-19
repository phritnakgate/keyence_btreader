package org.bkkz.keyence_btreader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [BarcodeLogRecord::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun barcodeDao(): BarcodeLogRecordDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java, "AppDatabase"
                ).build().also { instance = it }
            }
        }
    }
}