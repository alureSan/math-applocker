package com.mathapplocker.foss.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "custom_questions")
data class CustomQuestion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val text: String,
    val answer: Int,
)

@Dao
interface CustomQuestionDao {
    @Query("SELECT * FROM custom_questions ORDER BY id DESC")
    fun observeAll(): Flow<List<CustomQuestion>>

    @Query("SELECT * FROM custom_questions")
    suspend fun getAll(): List<CustomQuestion>

    @Insert
    suspend fun insert(question: CustomQuestion)

    @Delete
    suspend fun delete(question: CustomQuestion)
}

@Database(entities = [CustomQuestion::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customQuestions(): CustomQuestionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "math_applocker.db",
                ).build().also { instance = it }
            }
    }
}
