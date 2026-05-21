package com.example.myapplication

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class UserDto(
    val user_id: Int,
    val username: String,
    val email: String,
    val created_at: String,
    val is_active: Boolean
)

data class VideoDto(
    val video_id: Int,
    val user_id: Int,
    val title: String,
    val sport_id: Int,
    val file_path: String,
    val annotated_path: String?,
    val uploaded_at: String
)

data class VideoCreateRequest(
    val user_id: Int,
    val title: String,
    val sport_code: String,
    val file_path: String,
    val annotated_path: String? = null
)

data class AngleAnalysisIn(
    val angle: String,
    val percent_bad: Double,
    val mean_diff_deg: Double,
    val max_diff_deg: Double,
    val is_critical: Boolean
)

data class AnalysisCreateRequest(
    val video_id: Int,
    val overall_score: Double? = null,
    val charts_path: String? = null,
    val status: String = "completed",
    val angles: List<AngleAnalysisIn> = emptyList(),
    val recommendations: List<String> = emptyList()
)

data class AnalysisDto(
    val analysis_id: Int,
    val video_id: Int,
    val overall_score: Double?,
    val charts_path: String?,
    val status: String,
    val created_at: String
)

data class UserMistakeCreateRequest(
    val user_id: Int,
    val analysis_id: Int,
    val mistake_code: String,
    val severity_code: String? = null,
    val notes: String? = null
)

data class UserMistakeDetailDto(
    val user_mistake_id: Int,
    val analysis_id: Int,
    val mistake_code: String,
    val title: String,
    val description: String,
    val severity: String,
    val sport: String,
    val icon_code: String?,
    val icon_tint_hex: String?,
    val detected_at: String,
    val notes: String?
)

interface BackendAPI {
    @POST("users")
    suspend fun register(@Body body: RegisterRequest): UserDto

    @POST("login")
    suspend fun login(@Body body: LoginRequest): UserDto

    @GET("users/{userId}/videos")
    suspend fun listVideos(@Path("userId") userId: Int): List<VideoDto>

    @DELETE("videos/{videoId}")
    suspend fun deleteVideo(
        @Path("videoId") videoId: Int,
        @Query("user_id") userId: Int
    )

    @POST("videos")
    suspend fun createVideo(@Body body: VideoCreateRequest): VideoDto

    @POST("analyses")
    suspend fun createAnalysis(@Body body: AnalysisCreateRequest): AnalysisDto

    @POST("user_mistakes")
    suspend fun createUserMistake(@Body body: UserMistakeCreateRequest)

    @GET("users/{userId}/mistakes")
    suspend fun listUserMistakes(@Path("userId") userId: Int): List<UserMistakeDetailDto>
}

val backendApi: BackendAPI = Retrofit.Builder()
    .baseUrl(BACKEND_BASE_URL)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(BackendAPI::class.java)
