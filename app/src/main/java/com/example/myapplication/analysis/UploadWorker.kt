package com.example.myapplication.analysis

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.Gson
import java.io.File

class UploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val path = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val file = File(path)
        if (!file.exists()) return Result.failure()
        return try {
            val result = ServerAnalyzer(applicationContext).analyze(file)
            PendingAnalysisStore(applicationContext).markCompleted(path, result)
            val json = Gson().toJson(result)
            Result.success(workDataOf(KEY_RESULT_JSON to json))
        } catch (e: Exception) {
            Log.w(TAG, "upload failed for $path: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        const val KEY_FILE_PATH = "file_path"
        const val KEY_RESULT_JSON = "result_json"
        private const val TAG = "UploadWorker"
        private const val WORK_PREFIX = "analyze_video_"

        fun workName(file: File): String = WORK_PREFIX + file.name

        fun enqueue(context: Context, file: File) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(constraints)
                .setInputData(Data.Builder().putString(KEY_FILE_PATH, file.absolutePath).build())
                .addTag(WORK_PREFIX)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(file),
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
