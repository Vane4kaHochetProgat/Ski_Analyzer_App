package com.vane4ka.skianalyzer.analysis

import android.content.Context
import com.vane4ka.skianalyzer.AnalysisResult
import com.vane4ka.skianalyzer.ConnectivityObserver
import com.vane4ka.skianalyzer.OfflineMode
import com.vane4ka.skianalyzer.UserSession
import kotlinx.coroutines.flow.first
import java.io.File

sealed class AnalysisOutcome {
    data class Done(val result: AnalysisResult, val source: Source) : AnalysisOutcome()
    object QueuedOffline : AnalysisOutcome()
    data class Failed(val message: String) : AnalysisOutcome()

    enum class Source { SERVER, LOCAL }
}

class AnalysisOrchestrator(
    private val context: Context,
    private val connectivity: ConnectivityObserver = ConnectivityObserver(context),
    private val session: UserSession = UserSession(context),
    private val serverAnalyzer: VideoAnalyzer = ServerAnalyzer(context),
    private val localAnalyzer: VideoAnalyzer = LocalAnalyzer(context),
) {

    suspend fun analyze(file: File): AnalysisOutcome {
        if (connectivity.isOnlineNow()) {
            return try {
                AnalysisOutcome.Done(serverAnalyzer.analyze(file), AnalysisOutcome.Source.SERVER)
            } catch (e: Exception) {
                runOffline(file, e.message ?: "server error")
            }
        }
        return runOffline(file, "no network")
    }

    private suspend fun runOffline(file: File, reason: String): AnalysisOutcome {
        return when (session.offlineMode.first()) {
            OfflineMode.RUN_LOCALLY -> try {
                AnalysisOutcome.Done(localAnalyzer.analyze(file), AnalysisOutcome.Source.LOCAL)
            } catch (e: Exception) {
                AnalysisOutcome.Failed("Локальный анализ упал: ${e.message}")
            }
            OfflineMode.WAIT_FOR_INTERNET -> {
                PendingAnalysisStore(context).markQueued(file.absolutePath)
                UploadWorker.enqueue(context, file)
                AnalysisOutcome.QueuedOffline
            }
        }
    }
}
