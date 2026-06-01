package com.example.myapplication.analysis

import android.content.Context
import android.util.Log
import com.example.myapplication.AnalysisResult
import com.example.myapplication.uploadVideoForAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface VideoAnalyzer {
    suspend fun analyze(file: File): AnalysisResult
}

class ServerAnalyzer(private val context: android.content.Context? = null) : VideoAnalyzer {
    override suspend fun analyze(file: File): AnalysisResult = withContext(Dispatchers.IO) {
        val toUpload = if (context != null) transcodeForUpload(context, file) else file
        try {
            uploadVideoForAnalysis(toUpload)
        } finally {
            if (toUpload != file && toUpload.exists()) {
                runCatching { toUpload.delete() }
            }
        }
    }
}

class LocalAnalyzer(private val context: Context) : VideoAnalyzer {

    override suspend fun analyze(file: File): AnalysisResult = withContext(Dispatchers.Default) {
        val template = TemplateLoader.load(context)
        val frames = VideoFrameExtractor.extractFrames(file, numSamples = 50)
        if (frames.isEmpty()) error("Не удалось извлечь кадры из видео")

        val detector = YoloPoseDetector(context)
        val poses: List<PoseFrame17?> = try {
            frames.map { bmp ->
                try { detector.detect(bmp) } catch (e: Exception) {
                    Log.w(TAG, "detect failed: ${e.message}")
                    null
                } finally { bmp.recycle() }
            }
        } finally {
            detector.close()
        }

        val angles = calculateAngles(poses)
        compareToTemplate(angles, template)
    }

    companion object { private const val TAG = "LocalAnalyzer" }
}
