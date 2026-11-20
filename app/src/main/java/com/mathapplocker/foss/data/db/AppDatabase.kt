package com.mathapplocker.foss.data.db

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** A named group of user-authored questions, e.g. "Homework". */
@Entity(tableName = "question_sets")
data class QuestionSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val enabled: Boolean = true,
)

/** A set plus how many questions it contains, for the management list. */
data class QuestionSetSummary(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val questionCount: Int,
)

@Entity(
    tableName = "custom_questions",
    indices = [Index("setId")],
)
data class CustomQuestion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val setId: Long = 0L,
    val text: String,
    /** Numeric answer; unused (0) when [textAnswer] is set. */
    val answer: Int,
    /** Non-null makes this a text-answer question. */
    val textAnswer: String? = null,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
)

@Dao
interface QuestionSetDao {
    @Query(
        """
        SELECT s.id AS id, s.name AS name, s.enabled AS enabled,
               (SELECT COUNT(*) FROM custom_questions q WHERE q.setId = s.id) AS questionCount
        FROM question_sets s
        ORDER BY s.id ASC
        """,
    )
    fun observeAll(): Flow<List<QuestionSetSummary>>

    @Query("SELECT id FROM question_sets WHERE enabled = 1")
    suspend fun enabledIds(): List<Long>

    @Insert
    suspend fun insert(set: QuestionSet): Long

    @Query("UPDATE question_sets SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE question_sets SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM custom_questions WHERE setId = :setId")
    suspend fun deleteQuestions(setId: Long)

    @Delete
    suspend fun delete(set: QuestionSet)

    @Transaction
    suspend fun deleteWithQuestions(set: QuestionSet) {
        deleteQuestions(set.id)
        delete(set)
    }
}

@Dao
interface CustomQuestionDao {
    @Query("SELECT * FROM custom_questions WHERE setId = :setId ORDER BY id DESC")
    fun observeBySet(setId: Long): Flow<List<CustomQuestion>>

    @Query("SELECT * FROM custom_questions WHERE setId IN (:setIds) AND enabled = 1")
    suspend fun getForSets(setIds: List<Long>): List<CustomQuestion>

    @Insert
    suspend fun insert(question: CustomQuestion)

    @Update
    suspend fun update(question: CustomQuestion)

    @Query("UPDATE custom_questions SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Delete
    suspend fun delete(question: CustomQuestion)
}

@Database(
    entities = [QuestionSet::class, CustomQuestion::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionSets(): QuestionSetDao

    abstract fun customQuestions(): CustomQuestionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * v1 stored a single flat list of custom questions. The migration puts all
         * of them into an enabled "Custom" set so existing users keep their questions.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `question_sets` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `enabled` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "INSERT INTO `question_sets` (`id`, `name`, `enabled`) " +
                        "VALUES (1, 'Custom', 1)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_questions_new` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`setId` INTEGER NOT NULL, `text` TEXT NOT NULL, " +
                        "`answer` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "INSERT INTO `custom_questions_new` (`id`, `setId`, `text`, `answer`) " +
                        "SELECT `id`, 1, `text`, `answer` FROM `custom_questions`",
                )
                db.execSQL("DROP TABLE `custom_questions`")
                db.execSQL(
                    "ALTER TABLE `custom_questions_new` RENAME TO `custom_questions`",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_custom_questions_setId` " +
                        "ON `custom_questions` (`setId`)",
                )
            }
        }

        /** v3 adds free-text answers and a per-question on/off switch. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `custom_questions` ADD COLUMN `textAnswer` TEXT",
                )
                db.execSQL(
                    "ALTER TABLE `custom_questions` ADD COLUMN " +
                        "`enabled` INTEGER NOT NULL DEFAULT 1",
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "math_applocker.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
