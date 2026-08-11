package com.turbobar.ime.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromEntryKind(kind: EntryKind): String = kind.name

    @TypeConverter
    fun toEntryKind(value: String): EntryKind = EntryKind.valueOf(value)

    @TypeConverter
    fun fromInsertMode(mode: InsertMode): String = mode.name

    @TypeConverter
    fun toInsertMode(value: String): InsertMode = InsertMode.valueOf(value)
}

@Database(entities = [PrefixEntry::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class TurboBarDatabase : RoomDatabase() {
    abstract fun prefixDao(): PrefixDao

    companion object {
        @Volatile private var INSTANCE: TurboBarDatabase? = null

        fun getInstance(context: Context): TurboBarDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TurboBarDatabase::class.java,
                    "turbobar.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
