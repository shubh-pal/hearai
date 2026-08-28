package com.hearai.app.di

import android.content.Context
import androidx.room.Room
import com.hearai.app.data.db.HearAiDatabase
import com.hearai.app.data.db.SessionDao
import com.hearai.app.data.db.SummaryDao
import com.hearai.app.data.db.TranscriptSegmentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HearAiDatabase =
        Room.databaseBuilder(context, HearAiDatabase::class.java, HearAiDatabase.DATABASE_NAME).build()

    @Provides
    fun provideSessionDao(db: HearAiDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideTranscriptSegmentDao(db: HearAiDatabase): TranscriptSegmentDao = db.transcriptSegmentDao()

    @Provides
    fun provideSummaryDao(db: HearAiDatabase): SummaryDao = db.summaryDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        // The Gemini Live WebSocket connection can be long-lived while listening is active.
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
}
