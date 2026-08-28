package com.hearai.app.data.repo

import com.hearai.app.data.db.SessionDao
import com.hearai.app.data.db.SummaryDao
import com.hearai.app.data.db.TranscriptSegmentDao
import com.hearai.app.data.model.Session
import com.hearai.app.data.model.Summary
import com.hearai.app.data.model.TranscriptSegment
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * §7 Sessions: persisted locally, listable, exportable, deletable.
 * Owns the in-memory rolling transcript buffer described in §4 while a session is active, and
 * flushes it to Room ("Persisted to local storage per session on stop").
 */
@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val segmentDao: TranscriptSegmentDao,
    private val summaryDao: SummaryDao,
) {
    fun observeSessions(): Flow<List<Session>> = sessionDao.observeAll()

    fun observeSegments(sessionId: String): Flow<List<TranscriptSegment>> =
        segmentDao.observeForSession(sessionId)

    fun observeSummaries(sessionId: String): Flow<List<Summary>> =
        summaryDao.observeForSession(sessionId)

    fun observeAllSummaries(): Flow<List<Summary>> = summaryDao.observeAll()

    suspend fun startSession(): Session {
        val session = Session(id = UUID.randomUUID().toString(), startTime = System.currentTimeMillis(), endTime = null)
        sessionDao.upsert(session)
        return session
    }

    suspend fun endSession(sessionId: String, detectedLanguages: List<String>) {
        val session = sessionDao.getById(sessionId) ?: return
        sessionDao.update(
            session.copy(endTime = System.currentTimeMillis(), detectedLanguages = detectedLanguages),
        )
    }

    suspend fun appendSegment(sessionId: String, detectedLanguage: String, text: String, timestamp: Long = System.currentTimeMillis()) {
        segmentDao.insert(
            TranscriptSegment(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                timestamp = timestamp,
                detectedLanguage = detectedLanguage,
                text = text,
            ),
        )
    }

    suspend fun saveSummary(sessionId: String, rangeStart: Long, rangeEnd: Long, text: String) {
        summaryDao.insert(
            Summary(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                timeRangeStart = rangeStart,
                timeRangeEnd = rangeEnd,
                text = text,
            ),
        )
    }

    /** Text since the last summary, for the summarizer batch call (§4 Summarizer). */
    suspend fun segmentsSince(sessionId: String, sinceMillis: Long) =
        segmentDao.getSince(sessionId, sinceMillis)

    suspend fun deleteSession(session: Session) = sessionDao.delete(session)

    /** §6.8 auto-delete session history after N days. */
    suspend fun applyRetention(retentionDays: Int?) {
        if (retentionDays == null || retentionDays <= 0) return
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        sessionDao.deleteOlderThan(cutoff)
    }
}
