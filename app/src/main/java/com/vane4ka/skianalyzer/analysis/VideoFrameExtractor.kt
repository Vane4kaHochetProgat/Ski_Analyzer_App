package com.vane4ka.skianalyzer.analysis

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File

object VideoFrameExtractor {

    fun extractFrames(videoFile: File, numSamples: Int = 50): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoFile.absolutePath)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: return emptyList()

            val out = ArrayList<Bitmap>(numSamples)
            for (i in 0 until numSamples) {
                val tMs = (i.toLong() * (durationMs - 1)) / maxOf(1, numSamples - 1)
                val bmp = retriever.getFrameAtTime(
                    tMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: continue
                out += bmp
            }
            return out
        } finally {
            retriever.release()
        }
    }
}
