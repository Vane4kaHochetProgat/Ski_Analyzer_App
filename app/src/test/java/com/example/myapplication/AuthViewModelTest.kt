/**
 * JVM unit tests for [AuthViewModel].
 *
 * Test plumbing:
 *   * `Dispatchers.setMain(StandardTestDispatcher())` is installed in [setup]
 *     so `viewModelScope.launch` runs on a controllable dispatcher; tests
 *     drive it with `advanceUntilIdle()`.
 *   * The same `StandardTestDispatcher` is also injected as `ioDispatcher` so
 *     the `withContext(ioDispatcher)` block inside [AuthViewModel.submit]
 *     stays on the test scheduler.
 *   * [BackendAPI] and [UserSession] are mocked with MockK
 *     (`relaxed = true` on the session so `save`/`clear` are stubbed by
 *     default; `current` is a `flowOf(null)` so no user is signed in).
 *
 * Covered cases:
 *   * Happy paths for LOGIN and REGISTER — verifies API call, `session.save`,
 *     error cleared, `isSubmitting` flipped back to false.
 *   * Error mapping — 401 → InvalidCredentials, 409 → UserExists,
 *     500 → ServerError(code). (A `NetworkError` case is present but
 *     currently commented out.)
 *   * Concurrent `submit` calls are deduplicated — only one API hit fires
 *     while one is in flight.
 *   * `clearError()` resets `errorMessage` to null; `signOut()` calls
 *     `session.clear()`.
 */

package com.example.myapplication

import android.app.Application
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
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
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val app: Application = mockk(relaxed = true)
    private lateinit var api: BackendAPI
    private lateinit var session: UserSession

    private fun vm() = AuthViewModel(app, api, session, dispatcher)

    private val userDto = UserDto(
        user_id = 1,
        username = "ann",
        email = "ann@x",
        created_at = "now",
        is_active = true
    )
    private val authDto = AuthResponseDto(user = userDto, access_token = "jwt-abc")

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        api = mockk()
        session = mockk(relaxed = true)
        every { session.current } returns flowOf(null)
        coEvery { session.save(any(), any()) } just Runs
        coEvery { session.clear() } just Runs
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submit LOGIN success saves session and clears error`() = runTest(dispatcher) {
        coEvery { api.login(LoginRequest("ann@x", "secret")) } returns authDto

        val vm = vm()
        vm.submit(AuthMode.LOGIN, username = "", email = " ann@x ", password = "secret")
        advanceUntilIdle()

        coVerify { api.login(LoginRequest("ann@x", "secret")) }
        coVerify { session.save(userDto, "jwt-abc") }
        assertNull(vm.errorMessage.value)
        assertEquals(false, vm.isSubmitting.value)
    }

    @Test
    fun `submit REGISTER success calls register endpoint`() = runTest(dispatcher) {
        coEvery {
            api.register(RegisterRequest("ann", "ann@x", "secret"))
        } returns authDto

        val vm = vm()
        vm.submit(AuthMode.REGISTER, username = "ann", email = "ann@x", password = "secret")
        advanceUntilIdle()

        coVerify { api.register(RegisterRequest("ann", "ann@x", "secret")) }
        coVerify { session.save(userDto, "jwt-abc") }
    }

    @Test
    fun `submit emits InvalidCredentials on 401`() = runTest(dispatcher) {
        coEvery { api.login(any()) } throws HttpException(
            Response.error<AuthResponseDto>(401, "".toResponseBody())
        )

        val vm = vm()
        vm.submit(AuthMode.LOGIN, "", "ann@x", "wrong")
        advanceUntilIdle()

        assertEquals(AuthError.InvalidCredentials, vm.errorMessage.value)
        coVerify(exactly = 0) { session.save(any(), any()) }
    }

    @Test
    fun `submit emits UserExists on 409`() = runTest(dispatcher) {
        coEvery { api.register(any()) } throws HttpException(
            Response.error<AuthResponseDto>(409, "".toResponseBody())
        )

        val vm = vm()
        vm.submit(AuthMode.REGISTER, "ann", "ann@x", "secret")
        advanceUntilIdle()

        assertEquals(AuthError.UserExists, vm.errorMessage.value)
    }

    @Test
    fun `submit emits ServerError with code on 500`() = runTest(dispatcher) {
        coEvery { api.login(any()) } throws HttpException(
            Response.error<AuthResponseDto>(500, "".toResponseBody())
        )

        val vm = vm()
        vm.submit(AuthMode.LOGIN, "", "x@x", "y")
        advanceUntilIdle()

        assertEquals(AuthError.ServerError(500), vm.errorMessage.value)
    }

//    @Test
//    fun `submit emits NetworkError on generic exception`() = runTest(dispatcher) {
//        coEvery { api.login(any()) } throws java.net.SocketTimeoutException("read timeout")
//
//        val vm = vm()
//        vm.submit(AuthMode.LOGIN, "", "x@x", "y")
//        advanceUntilIdle()
//
//        val err = vm.errorMessage.value
//        assertTrue(err is AuthError.NetworkError)
//        assertTrue((err as AuthError.NetworkError).detail.contains("read timeout"))
//    }

    @Test
    fun `concurrent submits are ignored while one is in flight`() = runTest(dispatcher) {
        coEvery { api.login(any()) } coAnswers {
            kotlinx.coroutines.delay(50)
            authDto
        }

        val vm = vm()
        vm.submit(AuthMode.LOGIN, "", "x@x", "y")
        vm.submit(AuthMode.LOGIN, "", "x@x", "y")
        advanceUntilIdle()

        coVerify(exactly = 1) { api.login(any()) }
    }

    @Test
    fun `clearError sets errorMessage to null`() = runTest(dispatcher) {
        coEvery { api.login(any()) } throws HttpException(
            Response.error<AuthResponseDto>(401, "".toResponseBody())
        )
        val vm = vm()
        vm.submit(AuthMode.LOGIN, "", "x@x", "y")
        advanceUntilIdle()
        assertEquals(AuthError.InvalidCredentials, vm.errorMessage.value)

        vm.clearError()
        assertNull(vm.errorMessage.value)
    }

    @Test
    fun `signOut clears session`() = runTest(dispatcher) {
        val vm = vm()
        vm.signOut()
        advanceUntilIdle()
        coVerify { session.clear() }
    }
}
