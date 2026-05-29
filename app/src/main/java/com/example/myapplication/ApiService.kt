/**
 * Retrofit client for the video-analysis service (separate from the CRUD
 * [BackendAPI]).
 *
 * Endpoint:
 *   POST /analyze-video    — multipart upload of an `.mp4` returning a parsed
 *                            [AnalysisResult] (status, score, per-angle
 *                            metrics, recommendations, output file paths).
 *
 * The OkHttp client is configured with generous read/write/call timeouts
 * (5–10 min) because the server runs pose estimation per frame and can take
 * minutes on a long clip. [analyzeApi] is a module-level singleton built
 * against [ANALYZE_BASE_URL] from [PrivateConsts].
 *
 * Helper [uploadVideoForAnalysis] wraps a `File` into a `video/mp4` multipart
 * part keyed `file` — matches the FastAPI handler's expected form field name.
 */

package com.example.myapplication

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File
import java.util.concurrent.TimeUnit


interface ApiService {
    @Multipart
    @POST("analyze-video")
    suspend fun analyzeVideo(
        @Part video: MultipartBody.Part
    ): AnalysisResult
}

private val analyzeHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(5, TimeUnit.MINUTES)
    .readTimeout(5, TimeUnit.MINUTES)
    .callTimeout(10, TimeUnit.MINUTES)
    .build()

val analyzeApi: ApiService = Retrofit.Builder()
    .baseUrl(ANALYZE_BASE_URL)
    .client(analyzeHttpClient)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(ApiService::class.java)

suspend fun uploadVideoForAnalysis(file: File): AnalysisResult {
    val requestBody = file.asRequestBody("video/mp4".toMediaType())
    val part = MultipartBody.Part.createFormData("file", file.name, requestBody)
    return analyzeApi.analyzeVideo(part)
}