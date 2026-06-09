package com.vane4ka.skianalyzer

import android.app.Application
import io.mockk.coEvery
import io.mockk.every
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
import org.junit.Before
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val app: Application = mockk(relaxed = true)
    private lateinit var api: BackendAPI
    private lateinit var session: UserSession
    private val user = StoredUser(userId = 42, username = "vane4ka", email = "v@e.test")

    private fun vm() = ProfileViewModel(app, api, session, dispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        api = mockk()
        session = mockk()
        every { session.current } returns flowOf(user)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh fills username and email from session, counts from api`() = runTest(dispatcher) {
        coEvery { api.listMyVideos() } returns listOf(
            videoDto(1, uploadedAtIso = recentIso(days = 1)),
            videoDto(2, uploadedAtIso = recentIso(days = 3)),
            videoDto(3, uploadedAtIso = recentIso(days = 30)),
        )
        coEvery { api.listMyMistakes() } returns listOf(
            mistakeDto(title = "Leaning Back"),
            mistakeDto(title = "Leaning Back"),
            mistakeDto(title = "Stiff Knees"),
        )

        val vm = vm()
        vm.refresh()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("vane4ka", state.username)
        assertEquals("v@e.test", state.email)
        assertEquals(3, state.videosCount)
        assertEquals(3, state.mistakesCount)
        assertEquals("Leaning Back", state.topMistakeTitle)
        assertEquals(2, state.videosThisWeek)
    }

    @Test
    fun `refresh leaves videosCount null when listMyVideos throws`() = runTest(dispatcher) {
        coEvery { api.listMyVideos() } throws RuntimeException("offline")
        coEvery { api.listMyMistakes() } returns emptyList()

        val vm = vm()
        vm.refresh()
        advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.videosCount)
        assertEquals(0, state.mistakesCount)
        assertNull(state.videosThisWeek)
    }

    @Test
    fun `refresh leaves mistakesCount null when listMyMistakes throws`() = runTest(dispatcher) {
        coEvery { api.listMyVideos() } returns emptyList()
        coEvery { api.listMyMistakes() } throws RuntimeException("offline")

        val vm = vm()
        vm.refresh()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(0, state.videosCount)
        assertNull(state.mistakesCount)
        assertNull(state.topMistakeTitle)
    }

    @Test
    fun `refresh does nothing when user is not signed in`() = runTest(dispatcher) {
        every { session.current } returns flowOf(null)

        val vm = vm()
        vm.refresh()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("", state.username)
        assertEquals("", state.email)
        assertNull(state.videosCount)
        assertNull(state.mistakesCount)
    }

    @Test
    fun `refresh tolerates unparseable uploaded_at without crashing`() = runTest(dispatcher) {
        coEvery { api.listMyVideos() } returns listOf(
            videoDto(1, uploadedAtIso = "garbage"),
            videoDto(2, uploadedAtIso = recentIso(days = 1)),
        )
        coEvery { api.listMyMistakes() } returns emptyList()

        val vm = vm()
        vm.refresh()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2, state.videosCount)
        assertEquals(1, state.videosThisWeek)
    }

    @Test
    fun `refresh picks most frequent mistake title for topMistakeTitle`() = runTest(dispatcher) {
        coEvery { api.listMyVideos() } returns emptyList()
        coEvery { api.listMyMistakes() } returns listOf(
            mistakeDto(title = "A"),
            mistakeDto(title = "B"),
            mistakeDto(title = "B"),
            mistakeDto(title = "B"),
            mistakeDto(title = "C"),
            mistakeDto(title = "C"),
        )

        val vm = vm()
        vm.refresh()
        advanceUntilIdle()

        assertEquals("B", vm.state.value.topMistakeTitle)
    }

    private fun recentIso(days: Long): String =
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(days).toString()

    private fun videoDto(id: Int, uploadedAtIso: String) = VideoDto(
        video_id = id,
        user_id = user.userId,
        title = "v$id",
        sport_id = 1,
        file_path = "/p/$id.mp4",
        annotated_path = null,
        uploaded_at = uploadedAtIso,
    )

    private fun mistakeDto(title: String) = UserMistakeDetailDto(
        user_mistake_id = title.hashCode(),
        analysis_id = 1,
        mistake_code = title.lowercase(),
        title = title,
        description = "d",
        severity = "low",
        sport = "skiing",
        icon_code = null,
        icon_tint_hex = null,
        detected_at = recentIso(days = 0),
        notes = null,
    )
}
