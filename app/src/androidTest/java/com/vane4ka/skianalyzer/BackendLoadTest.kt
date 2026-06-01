package com.vane4ka.skianalyzer

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.system.measureTimeMillis

private const val VIRTUAL_USERS = 20
private const val ITERATIONS_PER_USER = 5
private const val TAG = "BackendLoadTest"

private val SPORTS = listOf("skiing", "snowboarding")
private val MISTAKE_CODES = listOf(
    "leaning_back", "arms_too_wide", "looking_down",
    "stiff_knees", "hip_rotation_issues",
)
private val ANGLE_NAMES = listOf(
    "left_knee_angle", "right_knee_angle", "hip_rotation_angle",
    "spine_tilt_angle", "shoulder_width_angle",
)

private interface LoadTestAPI {
    @POST("users")
    suspend fun register(@Body body: RegisterRequest): AuthResponseDto

    @POST("videos")
    suspend fun createVideo(
        @Header("Authorization") auth: String,
        @Body body: VideoCreateRequest,
    ): VideoDto

    @POST("analyses")
    suspend fun createAnalysis(
        @Header("Authorization") auth: String,
        @Body body: AnalysisCreateRequest,
    ): AnalysisDto

    @POST("user_mistakes")
    suspend fun createUserMistake(
        @Header("Authorization") auth: String,
        @Body body: UserMistakeCreateRequest,
    )

    @GET("me/mistakes")
    suspend fun listMyMistakes(
        @Header("Authorization") auth: String,
    ): List<UserMistakeDetailDto>
}

private fun buildLoadTestApi(): LoadTestAPI {
    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    return Retrofit.Builder()
        .baseUrl(BACKEND_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(LoadTestAPI::class.java)
}

@RunWith(AndroidJUnit4::class)
@LargeTest
class BackendLoadTest {

    @Test
    fun stressFullPipelineFromManyVirtualUsers() = runBlocking {
        val api = buildLoadTestApi()
        val ok = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val latencies = java.util.Collections.synchronizedList(mutableListOf<Long>())

        val total = measureTimeMillis {
            (1..VIRTUAL_USERS).map { vu ->
                async(Dispatchers.IO) { runVirtualUser(api, vu, ok, failed, latencies) }
            }.awaitAll()
        }

        val attempted = VIRTUAL_USERS * ITERATIONS_PER_USER
        val sorted = latencies.sorted()
        val avg = if (sorted.isEmpty()) 0L else sorted.average().toLong()
        val p50 = sorted.percentile(50)
        val p95 = sorted.percentile(95)
        val p99 = sorted.percentile(99)

        Log.i(
            TAG,
            """
            |==== Backend load test summary ====
            |virtual users      : $VIRTUAL_USERS
            |iters per user     : $ITERATIONS_PER_USER
            |pipelines attempted: $attempted
            |pipelines ok       : ${ok.get()}
            |pipelines failed   : ${failed.get()}
            |wall time          : ${total}ms
            |throughput         : ${"%.2f".format(ok.get() * 1000.0 / total)} ok/s
            |per-pipeline avg   : ${avg}ms
            |per-pipeline p50   : ${p50}ms
            |per-pipeline p95   : ${p95}ms
            |per-pipeline p99   : ${p99}ms
            |===================================
            """.trimMargin()
        )

        if (ok.get() == 0) {
            throw AssertionError("All pipelines failed — see logcat with tag $TAG")
        }
    }
}

private suspend fun runVirtualUser(
    api: LoadTestAPI,
    vu: Int,
    ok: AtomicInteger,
    failed: AtomicInteger,
    latencies: MutableList<Long>,
) = withContext(Dispatchers.IO) {
    val suffix = UUID.randomUUID().toString().take(10)
    val token = try {
        api.register(
            RegisterRequest(
                username = "u_${suffix}",
                email = "$suffix@load.test",
                password = "Secret_1234",
            )
        ).access_token
    } catch (e: Exception) {
        Log.w(TAG, "vu=$vu register failed: ${e.summary()}")
        failed.addAndGet(ITERATIONS_PER_USER)
        return@withContext
    }

    val auth = "Bearer $token"
    repeat(ITERATIONS_PER_USER) { iter ->
        val t0 = System.currentTimeMillis()
        try {
            val title = "load_vu${vu}_i${iter}_${UUID.randomUUID().toString().take(4)}"
            val video = api.createVideo(
                auth,
                VideoCreateRequest(
                    title = title,
                    sport_code = SPORTS.random(),
                    file_path = "/tmp/$title.mp4",
                ),
            )

            val angleCount = Random.nextInt(2, 5)
            val analysis = api.createAnalysis(
                auth,
                AnalysisCreateRequest(
                    video_id = video.video_id,
                    overall_score = ((Random.nextDouble(40.0, 95.0)) * 10).toInt() / 10.0,
                    status = "completed",
                    angles = ANGLE_NAMES.shuffled().take(angleCount).map { name ->
                        AngleAnalysisIn(
                            angle = name,
                            percent_bad = Random.nextDouble(20.0, 90.0),
                            mean_diff_deg = Random.nextDouble(2.0, 15.0),
                            max_diff_deg = Random.nextDouble(10.0, 40.0),
                            is_critical = Random.nextDouble() < 0.3,
                        )
                    },
                    recommendations = listOf("Согните колени", "Смотрите вперёд"),
                ),
            )

            try {
                api.createUserMistake(
                    auth,
                    UserMistakeCreateRequest(
                        analysis_id = analysis.analysis_id,
                        mistake_code = MISTAKE_CODES.random(),
                    ),
                )
            } catch (e: HttpException) {
                if (e.code() != 409) throw e
            }

            ok.incrementAndGet()
        } catch (e: Exception) {
            failed.incrementAndGet()
            Log.w(TAG, "vu=$vu iter=$iter failed: ${e.summary()}")
        } finally {
            latencies.add(System.currentTimeMillis() - t0)
        }
    }
}

private fun Exception.summary(): String = when (this) {
    is HttpException -> "HTTP ${code()} ${runCatching { response()?.errorBody()?.string()?.take(120) }.getOrNull().orEmpty()}"
    else -> "${javaClass.simpleName}: ${message?.take(120)}"
}

private fun List<Long>.percentile(p: Int): Long {
    if (isEmpty()) return 0
    val idx = ((size - 1) * p / 100).coerceIn(0, size - 1)
    return this[idx]
}
