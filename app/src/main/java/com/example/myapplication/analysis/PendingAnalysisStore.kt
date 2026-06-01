package com.example.myapplication.analysis

import android.content.Context
import com.example.myapplication.AnalysisResult
import com.google.gson.Gson

enum class PendingStatus { QUEUED, COMPLETED, FAILED }

class PendingAnalysisStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun markQueued(filePath: String) {
        prefs.edit().putString(statusKey(filePath), PendingStatus.QUEUED.name)
            .remove(resultKey(filePath))
            .apply()
    }

    fun markCompleted(filePath: String, result: AnalysisResult) {
        prefs.edit()
            .putString(statusKey(filePath), PendingStatus.COMPLETED.name)
            .putString(resultKey(filePath), Gson().toJson(result))
            .apply()
    }

    fun status(filePath: String): PendingStatus? =
        prefs.getString(statusKey(filePath), null)?.let { runCatching { PendingStatus.valueOf(it) }.getOrNull() }

    fun result(filePath: String): AnalysisResult? {
        val json = prefs.getString(resultKey(filePath), null) ?: return null
        return runCatching { Gson().fromJson(json, AnalysisResult::class.java) }.getOrNull()
    }

    fun clear(filePath: String) {
        prefs.edit().remove(statusKey(filePath)).remove(resultKey(filePath)).apply()
    }

    companion object {
        private const val PREFS = "pending_analysis"
        private fun statusKey(p: String) = "status::$p"
        private fun resultKey(p: String) = "result::$p"
    }
}
