/**
 * JVM unit tests for [MistakesViewModel].
 *
 * Test plumbing mirrors [AuthViewModelTest]: a `StandardTestDispatcher` is
 * installed as `Dispatchers.Main` and injected as the ViewModel's IO
 * dispatcher; [BackendAPI] is mocked with MockK, [UserSession.current]
 * emits a single fixed [StoredUser] (`user_id = 7`) so the "signed-in"
 * branch is exercised by default.
 *
 * Covered cases:
 *   * `refresh()` state transitions — Loaded on success, Error("boom") when
 *     the API throws, NotSignedIn when `session.current` emits null.
 *   * `recordAnalysis()` happy path — verifies the exact call sequence with
 *     `coVerifySequence`: createVideo → createAnalysis → one
 *     createUserMistake per mapped angle → listUserMistakes (the `refresh`
 *     call). Also asserts that `mistakesFrom` produced "hip_rotation_issues"
 *     (high) and "stiff_knees" (medium) from the input angles.
 *   * `recordAnalysis()` swallows 409 from `createUserMistake` and still
 *     refreshes.
 *   * `recordAnalysis()` returns silently (zero API calls) when no user is
 *     signed in.
 *   * `mistakesFromForTest` exercises the angle→code/severity heuristic:
 *     name-substring matching, severity bucketing (critical → high, ≥70 →
 *     high, ≥50 → medium, ≥40 → low), dedup-by-code, and the cutoff that
 *     drops angles with `percent_bad < 40 && !is_critical`.
 */

package com.vane4ka.skianalyzer

import android.app.Application
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MistakesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val app: Application = mockk(relaxed = true)
    private lateinit var api: BackendAPI
    private lateinit var session: UserSession
    private val user = StoredUser(userId = 7, username = "u", email = "u@e")

    private fun vm() = MistakesViewModel(app, api, session, dispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        api = mockk(relaxed = false)
        session = mockk(relaxed = true)
        every { session.current } returns flowOf(user)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh emits Loaded on success`() = runTest(dispatcher) {
        val rows = listOf(sampleMistake(1, "leaning_back"))
        coEvery { api.listMyMistakes() } returns rows

        val vm = vm()
        vm.refresh()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue("expected Loaded, got $state", state is MistakesUiState.Loaded)
        assertEquals(rows, (state as MistakesUiState.Loaded).mistakes)
    }

    @Test
    fun `refresh emits Error when api throws`() = runTest(dispatcher) {
        coEvery { api.listMyMistakes() } throws RuntimeException("boom")

        val vm = vm()
        vm.refresh()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is MistakesUiState.Error)
        assertEquals("boom", (state as MistakesUiState.Error).message)
    }

    @Test
    fun `refresh emits NotSignedIn when not signed in`() = runTest(dispatcher) {
        every { session.current } returns flowOf(null)

        val vm = vm()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(MistakesUiState.NotSignedIn, vm.state.value)
    }

    @Test
    fun `recordAnalysis posts video, analysis, mistakes then refreshes`() = runTest(dispatcher) {
        val analysisResult = AnalysisResult(
            status = "completed",
            analysis = Analysis(
                overall_score = 72.0,
                angle_analysis = listOf(
                    AngleAnalysis("Hip", percent_bad = 80.0, mean_diff_deg = 8.0, max_diff_deg = 20.0, is_critical = true),
                    AngleAnalysis("Knee", percent_bad = 55.0, mean_diff_deg = 5.0, max_diff_deg = 12.0, is_critical = false),
                    AngleAnalysis("Elbow", percent_bad = 10.0, mean_diff_deg = 1.0, max_diff_deg = 3.0, is_critical = false)
                ),
                recommendations = listOf("Bend knees more")
            ),
            files = Files(annotated_video = "annot.mp4", charts = "ch.png")
        )
        coEvery { api.createVideo(any()) } returns VideoDto(
            video_id = 42, user_id = 7, title = "x", sport_id = 1,
            file_path = "/p", annotated_path = "annot.mp4", uploaded_at = "now"
        )
        coEvery { api.createAnalysis(any()) } returns AnalysisDto(
            analysis_id = 99, video_id = 42, overall_score = 72.0,
            charts_path = "ch.png", status = "completed", created_at = "now"
        )
        coEvery { api.createUserMistake(any()) } just Runs
        coEvery { api.listMyMistakes() } returns emptyList()

        val vm = vm()
        vm.recordAnalysis(File("/tmp/clip.mp4"), "skiing", analysisResult)
        advanceUntilIdle()

        coVerifySequence {
            session.current
            api.createVideo(match { it.title == "clip" && it.sport_code == "skiing" })
            api.createAnalysis(match { it.video_id == 42 && it.angles.size == 3 })
            api.createUserMistake(match { it.mistake_code == "hip_rotation_issues" && it.severity_code == "high" })
            api.createUserMistake(match { it.mistake_code == "stiff_knees" && it.severity_code == "medium" })
            session.current
            api.listMyMistakes()
        }
    }

    @Test
    fun `recordAnalysis ignores 409 from createUserMistake`() = runTest(dispatcher) {
        val result = AnalysisResult(
            status = "completed",
            analysis = Analysis(
                overall_score = 50.0,
                angle_analysis = listOf(
                    AngleAnalysis("Hip", 90.0, 9.0, 22.0, is_critical = true)
                ),
                recommendations = emptyList()
            ),
            files = Files(annotated_video = null, charts = null)
        )
        coEvery { api.createVideo(any()) } returns VideoDto(1, 7, "t", 1, "/p", null, "now")
        coEvery { api.createAnalysis(any()) } returns AnalysisDto(1, 1, null, null, "completed", "now")
        coEvery { api.createUserMistake(any()) } throws HttpException(
            Response.error<Unit>(409, "".toResponseBody())
        )
        coEvery { api.listMyMistakes() } returns emptyList()

        val vm = vm()
        vm.recordAnalysis(File("/tmp/a.mp4"), "skiing", result)
        advanceUntilIdle()

        coVerify { api.listMyMistakes() }
    }

    @Test
    fun `recordAnalysis returns silently when not signed in`() = runTest(dispatcher) {
        every { session.current } returns flowOf(null)

        val vm = vm()
        vm.recordAnalysis(File("/x.mp4"), "skiing", sampleAnalysis())
        advanceUntilIdle()

        coVerify(exactly = 0) { api.createVideo(any()) }
    }

    @Test
    fun `mistakesFrom maps angle names and severities correctly`() {
        val angles = listOf(
            AngleAnalysis("Right Hip Angle", 80.0, 8.0, 20.0, is_critical = true),
            AngleAnalysis("Knee Flexion",     55.0, 5.0, 12.0, is_critical = false),
            AngleAnalysis("Spine Tilt",       45.0, 4.5, 10.0, is_critical = false),
            AngleAnalysis("Head Yaw",         72.0, 7.2, 16.0, is_critical = false),
            AngleAnalysis("Ankle Pronation",  85.0, 8.5, 18.0, is_critical = true),
            AngleAnalysis("Hip Adduction",    99.0, 9.9, 25.0, is_critical = true)
        )
        val mapped = mistakesFromForTest(angles).toMap()
        assertEquals("high", mapped["hip_rotation_issues"])
        assertEquals("medium", mapped["stiff_knees"])
        assertEquals("low", mapped["leaning_back"])
        assertEquals("high", mapped["looking_down"])
        assertNull(mapped["arms_too_wide"])
        assertEquals(4, mapped.size)
    }

    @Test
    fun `mistakesFrom skips angles that are neither critical nor over 40 percent bad`() {
        val angles = listOf(
            AngleAnalysis("Hip", 39.9, 4.0, 10.0, is_critical = false)
        )
        assertTrue(mistakesFromForTest(angles).isEmpty())
    }

    private fun sampleMistake(id: Int, code: String) = UserMistakeDetailDto(
        user_mistake_id = id,
        analysis_id = 1,
        mistake_code = code,
        title = "T",
        description = "D",
        severity = "high",
        sport = "skiing",
        icon_code = "warning",
        icon_tint_hex = "#FF0000",
        detected_at = "2026-05-21T10:00:00Z",
        notes = null
    )

    private fun sampleAnalysis() = AnalysisResult(
        status = "completed",
        analysis = Analysis(50.0, emptyList(), emptyList()),
        files = Files(null, null)
    )
}
