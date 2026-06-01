package com.example.myapplication.analysis

import android.content.Context
import android.net.Uri
import android.util.Log
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.VideoQuality
import com.abedelazizshe.lightcompressorlibrary.config.AppSpecificStorageConfiguration
import com.abedelazizshe.lightcompressorlibrary.config.Configuration
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "VideoTranscoder"
private const val SKIP_BELOW_BYTES = 30L * 1024 * 1024

suspend fun transcodeForUpload(context: Context, src: File): File {
    if (!src.exists()) throw IOException("Файл не найден: ${src.absolutePath}")
    val srcBytes = src.length()
    if (srcBytes < SKIP_BELOW_BYTES) {
        Log.i(TAG, "skip: ${src.name} = ${srcBytes / 1024} KB < threshold")
        return src
    }
    return suspendCancellableCoroutine { cont ->
        val outputName = "compressed_${System.currentTimeMillis()}"
        Log.i(TAG, "start: ${src.name} (${srcBytes / 1024 / 1024} MB) → $outputName.mp4")

        VideoCompressor.start(
            context = context.applicationContext,
            uris = listOf(Uri.fromFile(src)),
            isStreamable = false,
            sharedStorageConfiguration = null,
            appSpecificStorageConfiguration = AppSpecificStorageConfiguration(
                subFolderName = null,
            ),
            configureWith = Configuration(
                videoNames = listOf(outputName),
                quality = VideoQuality.MEDIUM,
                videoBitrateInMbps = 5,
                disableAudio = true,
                keepOriginalResolution = false,
            ),
            listener = CompressionResultListener(cont, srcBytes),
        )
    }
}

private class CompressionResultListener(
    private val cont: CancellableContinuation<File>,
    private val srcBytes: Long,
) : CompressionListener {
    override fun onStart(index: Int) {}
    override fun onProgress(index: Int, percent: Float) {}
    override fun onSuccess(index: Int, size: Long, path: String?) {
        if (!cont.isActive) return
        if (path == null) {
            cont.resumeWithException(IOException("Compressor вернул null path"))
            return
        }
        val out = File(path)
        Log.i(TAG, "done: ${out.name} = ${size / 1024 / 1024} MB (было ${srcBytes / 1024 / 1024} MB)")
        cont.resume(out)
    }
    override fun onFailure(index: Int, failureMessage: String) {
        if (cont.isActive) cont.resumeWithException(IOException("Transcode failed: $failureMessage"))
    }
    override fun onCancelled(index: Int) {
        if (cont.isActive) cont.resumeWithException(CancellationException("Transcode cancelled"))
    }
}
