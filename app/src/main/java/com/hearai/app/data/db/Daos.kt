package com.hearai.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hearai.app.data.model.Session
import com.hearai.app.data.model.Summary
import com.hearai.app.data.model.TranscriptSegment
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: Session)

    @Update
    suspend fun update(session: Session)

    @Delete
    suspend fun delete(session: Session)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteById(sessionId: String)

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun observeAll(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getById(sessionId: String): Session?

    /** §6.8 auto-delete history after N days: purge sessions that ended before the cutoff. */
    @Query("DELETE FROM sessions WHERE endTime IS NOT NULL AND endTime < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}

@Dao
interface TranscriptSegmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(segment: TranscriptSegment)

    @Query("SELECT * FROM transcript_segments WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeForSession(sessionId: String): Flow<List<TranscriptSegment>>

    @Query("SELECT * FROM transcript_segments WHERE sessionId = :sessionId AND timestamp >= :sinceMillis ORDER BY timestamp ASC")
    suspend fun getSince(sessionId: String, sinceMillis: Long): List<TranscriptSegment>
}

@Dao
interface SummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: Summary)

    @Query("SELECT * FROM summaries ORDER BY timeRangeStart DESC")
    fun observeAll(): Flow<List<Summary>>

    @Query("SELECT * FROM summaries WHERE sessionId = :sessionId ORDER BY timeRangeStart ASC")
    fun observeForSession(sessionId: String): Flow<List<Summary>>
}
