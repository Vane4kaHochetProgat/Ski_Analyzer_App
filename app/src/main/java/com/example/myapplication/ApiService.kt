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