/**
 * Retrofit interface for the FastAPI backend (`server/app/main.py`).
 *
 * Wraps the CRUD-over-Postgres service the app talks to:
 *   * `register` / `login`                    — user accounts.
 *   * `listVideos` / `createVideo` / `deleteVideo` — video registry.
 *   * `createAnalysis`                        — persists scores, angles, tips.
 *   * `createUserMistake` / `listUserMistakes`— per-user mistake history.
 *
 * Request and response DTOs declared in this file mirror the server's Pydantic
 * models 1:1 — snake_case names matter, they're the wire format consumed by
 * Gson on the client and FastAPI on the server.
 *
 * The Retrofit client is built once as a module-level singleton [backendApi]
 * with [BACKEND_BASE_URL] from [PrivateConsts]; there is no DI container.
 * `AuthViewModel`, `MistakesViewModel`, and `ProfileViewModel` default to this
 * singleton but accept overrides via @JvmOverloads for unit tests.
 */

package com.vane4ka.skianalyzer

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

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

data class AuthResponseDto(
    val user: UserDto,
    val access_token: String,
    val token_type: String = "bearer",
)

data class VideoCreateRequest(
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
    suspend fun register(@Body body: RegisterRequest): AuthResponseDto

    @POST("login")
    suspend fun login(@Body body: LoginRequest): AuthResponseDto

    @GET("me/videos")
    suspend fun listMyVideos(): List<VideoDto>

    @DELETE("videos/{videoId}")
    suspend fun deleteVideo(@Path("videoId") videoId: Int)

    @POST("videos")
    suspend fun createVideo(@Body body: VideoCreateRequest): VideoDto

    @POST("analyses")
    suspend fun createAnalysis(@Body body: AnalysisCreateRequest): AnalysisDto

    @POST("user_mistakes")
    suspend fun createUserMistake(@Body body: UserMistakeCreateRequest)

    @GET("me/mistakes")
    suspend fun listMyMistakes(): List<UserMistakeDetailDto>
}

private val backendHttpClient: OkHttpClient by lazy {
    val builder = OkHttpClient.Builder().addInterceptor(AuthInterceptor())
    backendAppContext?.let { ctx ->
        builder.authenticator(UnauthorizedAuthenticator(ctx))
    }
    builder.build()
}

@Volatile private var backendAppContext: android.content.Context? = null

fun installBackendContext(ctx: android.content.Context) {
    backendAppContext = ctx.applicationContext
}

val backendApi: BackendAPI by lazy {
    Retrofit.Builder()
        .baseUrl(BACKEND_BASE_URL)
        .client(backendHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(BackendAPI::class.java)
}
