package com.example.childlocate.data.model

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName= "app_limits")
class AppLimitEntity (
    @PrimaryKey val packageName:String,
    val dailyLimitMinutes: Int,
    val startTime: String,
    val endTime: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis()
)

@Dao
interface AppLimitDao {
    @Query("SELECT * FROM app_limits")
    suspend fun getAllAppLimits(): List<AppLimitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppLimit(appLimit: AppLimitEntity)

    @Query("DELETE FROM app_limits WHERE packageName = :packageName")
    suspend fun deleteAppLimit(packageName: String)

    @Query("DELETE FROM app_limits")
    suspend fun deleteAll()
}

@Database(entities = [AppLimitEntity::class], version = 1, exportSchema = false)
abstract class AppLimitDatabase : RoomDatabase() {
    abstract fun appLimitDao(): AppLimitDao

    companion object {
        @Volatile
        private var INSTANCE: AppLimitDatabase? = null

        fun getInstance(context: Context): AppLimitDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppLimitDatabase::class.java,
                    "app_limits.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}