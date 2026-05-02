package com.scribe.app.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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

@Database(entities = [MessageEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
