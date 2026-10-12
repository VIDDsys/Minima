package com.minima.chat.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE archived = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeActive(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE archived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY pinned DESC, updatedAt DESC")
    suspend fun listAll(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE sessions SET pinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, updatedAt: Long)

    @Query("UPDATE sessions SET archived = :archived, pinned = CASE WHEN :archived = 1 THEN 0 ELSE pinned END, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, updatedAt: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeBySession(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun listBySession(sessionId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateContent(id: String, content: String)

    @Query(
        """
        UPDATE messages SET content = :content,
          inputTokens = :inputTokens, outputTokens = :outputTokens
        WHERE id = :id
        """,
    )
    suspend fun updateContentAndUsage(
        id: String,
        content: String,
        inputTokens: Int,
        outputTokens: Int,
    )

    @Query(
        """
        UPDATE messages SET inputTokens = :inputTokens, outputTokens = :outputTokens
        WHERE id = :id
        """,
    )
    suspend fun updateUsage(id: String, inputTokens: Int, outputTokens: Int)

    @Query(
        """
        UPDATE messages SET toolResult = :result, toolStatus = :status, content = :content
        WHERE id = :id
        """,
    )
    suspend fun updateToolResult(id: String, result: String, status: String, content: String)

    @Query(
        """
        SELECT * FROM messages
        WHERE sessionId = :sessionId AND toolCallId = :toolCallId
        LIMIT 1
        """,
    )
    suspend fun findByToolCallId(sessionId: String, toolCallId: String): MessageEntity?

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId AND createdAt > :afterCreatedAt")
    suspend fun deleteAfter(sessionId: String, afterCreatedAt: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId AND createdAt >= :fromCreatedAt")
    suspend fun deleteFrom(sessionId: String, fromCreatedAt: Long)

    @Query(
        """
        SELECT * FROM messages
        WHERE sessionId = :sessionId AND kind = 'tool' AND toolStatus = 'running'
          AND createdAt <= :createdAtCeilingMs
        """,
    )
    suspend fun listRunningTools(sessionId: String, createdAtCeilingMs: Long): List<MessageEntity>
}
