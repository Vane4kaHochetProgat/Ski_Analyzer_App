package com.vane4ka.skianalyzer.analysis

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException

object AnalysisTimingLog {

    private const val FILE_NAME = "analysis_timings.csv"
    private const val HEADER = "ts_ms,outcome,file_name,file_size_bytes,video_duration_ms,elapsed_ms\n"

    @Synchronized
    fun append(
        context: Context,
        outcome: String,
        file: File,
        elapsedMs: Long,
    ) {
        val target = File(context.getExternalFilesDir(null), FILE_NAME)
        try {
            val needsHeader = !target.exists() || target.length() == 0L
            val durationMs = videoDurationMs(file)
            FileWriter(target, true).use { w ->
                if (needsHeader) w.write(HEADER)
                w.write(
                    "${System.currentTimeMillis()}," +
                        "$outcome," +
                        "${file.name.replace(',', '_')}," +
                        "${file.length()}," +
                        "$durationMs," +
                        "$elapsedMs\n"
                )
            }
        } catch (e: IOException) {
            Log.w("AnalysisTimingLog", "failed to write $FILE_NAME: ${e.message}")
        }
    }

    private fun videoDurationMs(file: File): Long {
        if (!file.exists() || file.length() == 0L) return -1L
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(file.absolutePath)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1L
        } catch (_: Exception) {
            -1L
        } finally {
            runCatching { mmr.release() }
        }
    }
}
