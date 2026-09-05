package com.clawdroid.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "conversations",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("projectId"), Index("updatedAt")],
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val projectId: String?,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: String,
    val costUsd: Double,
    val summaryMessageId: String? = null,
    val lastPromptTokens: Int = 0,
    val totalPromptTokens: Long = 0,
    val totalCompletionTokens: Long = 0,
    val totalCachedTokens: Long = 0,
    val modelId: String = "",
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("conversationId"), Index("createdAt")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val tokenCount: Int,
    val toolCallId: String? = null, // For role="tool": the tool_call_id this result responds to
)

data class MessageWithToolCalls(
    @androidx.room.Embedded val message: MessageEntity,
    @androidx.room.Relation(
        parentColumn = "id",
        entityColumn = "messageId"
    )
    val toolCalls: List<ToolCallEntity>
)


@Entity(
    tableName = "tool_calls",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("messageId"), Index("status")],
)
data class ToolCallEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val toolName: String,
    val arguments: String,
    val result: String?,
    val status: String,
    val durationMs: Long,
)

@Entity(
    tableName = "automations",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("projectId"), Index("enabled")],
)
data class AutomationEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val prompt: String,
    val cronExpression: String,
    val enabled: Boolean,
    val lastRunAt: Long?,
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProjectEntity?

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun observeForProject(projectId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getMostRecent(): ConversationEntity?

    @Query("UPDATE conversations SET summaryMessageId = :summaryMessageId, lastPromptTokens = 0, updatedAt = :now WHERE id = :id")
    suspend fun setSummaryMessageId(id: String, summaryMessageId: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET lastPromptTokens = :lastPromptTokens, totalPromptTokens = totalPromptTokens + :promptTokens, totalCompletionTokens = totalCompletionTokens + :completionTokens, totalCachedTokens = totalCachedTokens + :cachedTokens, costUsd = costUsd + :costDelta, updatedAt = :now WHERE id = :id")
    suspend fun recordUsage(id: String, lastPromptTokens: Int, promptTokens: Long, completionTokens: Long, cachedTokens: Long, costDelta: Double, now: Long = System.currentTimeMillis())
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @androidx.room.Transaction
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessagesWithToolCalls(conversationId: String): Flow<List<MessageWithToolCalls>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: String)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getAll(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MessageEntity?

    @Update
    suspend fun update(message: MessageEntity)
}

@Dao
interface ToolCallDao {
    @Query("SELECT * FROM tool_calls WHERE messageId = :messageId ORDER BY id ASC")
    fun observeForMessage(messageId: String): Flow<List<ToolCallEntity>>

    @Query("SELECT * FROM tool_calls WHERE messageId = :messageId ORDER BY id ASC")
    suspend fun getForMessage(messageId: String): List<ToolCallEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(toolCall: ToolCallEntity)

    @Query("UPDATE tool_calls SET result = :result, status = :status, durationMs = :durationMs WHERE id = :id")
    suspend fun complete(id: String, result: String, status: String, durationMs: Long)
}

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automations ORDER BY name ASC")
    fun observeAutomations(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations WHERE enabled = 1 ORDER BY name ASC")
    suspend fun getEnabled(): List<AutomationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(automation: AutomationEntity)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    fun observe(key: String): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: SettingsEntity)
}

@Database(
    entities = [
        ProjectEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        ToolCallEntity::class,
        AutomationEntity::class,
        SettingsEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ClawDroidDatabase : RoomDatabase() {
    abstract fun projects(): ProjectDao
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun toolCalls(): ToolCallDao
    abstract fun automations(): AutomationDao
    abstract fun settings(): SettingsDao

    companion object {
        @Volatile private var instance: ClawDroidDatabase? = null

        fun get(context: Context): ClawDroidDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ClawDroidDatabase::class.java,
                "clawdroid.db",
            ).fallbackToDestructiveMigration(true).build().also { instance = it }
        }
    }
}
