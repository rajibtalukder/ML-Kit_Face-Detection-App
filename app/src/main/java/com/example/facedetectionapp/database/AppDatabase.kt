package com.example.facedetectionapp.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [UserFaceEntity::class], version = 1, exportSchema = false)
@TypeConverters(FaceIdConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userFaceDao(): UserFaceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "face_attendance_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}