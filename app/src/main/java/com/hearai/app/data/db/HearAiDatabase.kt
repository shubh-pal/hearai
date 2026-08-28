package com.hearai.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hearai.app.data.model.Session
import com.hearai.app.data.model.Summary
import com.hearai.app.data.model.TranscriptSegment

/**
 * Local-only persistence (§7: Sessions persisted locally, listable, exportable, deletable;
 * §8 Privacy: session data stays local unless the user explicitly exports it).
 */
@Database(
    entities = [Session::class, TranscriptSegment::class, Summary::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class HearAiDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun transcriptSegmentDao(): TranscriptSegmentDao
    abstract fun summaryDao(): SummaryDao

    companion object {
        const val DATABASE_NAME = "hearai.db"
    }
}
