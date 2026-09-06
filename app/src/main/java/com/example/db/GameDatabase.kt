package com.example.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "game_logs")
data class GameLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val gameType: String, // "Bots", "WiFi Host", "WiFi Guest", "WiFi Banker"
    val playerName: String,
    val balanceBefore: Int,
    val balanceAfter: Int,
    val amountGainedOrLost: Int, // positive or negative
    val summary: String
)

@Dao
interface GameLogDao {
    @Query("SELECT * FROM game_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<GameLog>>

    @Insert
    suspend fun insertLog(log: GameLog)

    @Query("DELETE FROM game_logs")
    suspend fun clearAllLogs()
}

@Database(entities = [GameLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameLogDao(): GameLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "poker_game_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class GameLogRepository(private val gameLogDao: GameLogDao) {
    val allLogs: Flow<List<GameLog>> = gameLogDao.getAllLogs()

    suspend fun insert(log: GameLog) {
        gameLogDao.insertLog(log)
    }

    suspend fun clearLogs() {
        gameLogDao.clearAllLogs()
    }
}
