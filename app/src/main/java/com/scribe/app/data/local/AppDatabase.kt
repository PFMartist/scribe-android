package com.scribe.app.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "title") val title: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "summary") val summary: String? = null
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "skill_id") val skillId: String? = null,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "incomplete", defaultValue = "0") val incomplete: Boolean = false
)

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title WHERE id = :convId")
    suspend fun updateTitle(convId: String, title: String)

    @Query("SELECT title FROM conversations WHERE id = :convId")
    suspend fun getTitle(convId: String): String?

    @Query("SELECT * FROM conversations")
    suspend fun getAllTitles(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :convId")
    suspend fun getConversation(convId: String): ConversationEntity?

    @Query("DELETE FROM conversations WHERE id = :convId")
    suspend fun delete(convId: String)

    @Query("UPDATE conversations SET summary = :summary WHERE id = :convId")
    suspend fun updateSummary(convId: String, summary: String)

    @Query("SELECT summary FROM conversations WHERE id = :convId")
    suspend fun getSummary(convId: String): String?

    @Query("UPDATE conversations SET summary = NULL WHERE id = :convId")
    suspend fun clearSummary(convId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversation_id = :convId ORDER BY timestamp ASC")
    suspend fun getMessages(convId: String): List<MessageEntity>

    @Insert
    suspend fun insert(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversation_id = :convId")
    suspend fun deleteConversation(convId: String)

    @Query("SELECT conversation_id FROM messages GROUP BY conversation_id ORDER BY MAX(timestamp) DESC")
    suspend fun getAllConversationIds(): List<String>

    @Query("SELECT content FROM messages WHERE conversation_id = :convId AND role = 'user' ORDER BY timestamp ASC LIMIT 1")
    suspend fun getFirstUserMessage(convId: String): String?

    @Query("UPDATE messages SET skill_id = :skillId WHERE conversation_id = :convId")
    suspend fun updateSkillId(convId: String, skillId: String?)
}

@Database(entities = [ConversationEntity::class, MessageEntity::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN summary TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS conversations (id TEXT NOT NULL PRIMARY KEY, title TEXT, created_at INTEGER NOT NULL DEFAULT 0)")
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN skill_id TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN incomplete INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scribe_history.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
