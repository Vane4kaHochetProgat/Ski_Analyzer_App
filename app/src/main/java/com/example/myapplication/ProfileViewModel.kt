/**
 * ViewModel for the Profile tab.
 *
 * Holds [ProfileUiState] (username, email, videos count) as a `StateFlow`.
 * [refresh] reads the currently signed-in user from [UserSession], then makes
 * an off-main `api.listVideos(userId)` call and exposes its size as
 * `videosCount`. A failed video fetch leaves `videosCount = null` (the UI
 * shows "—") instead of bubbling the error to the user.
 *
 * Constructor params are overridable for tests — production callers use
 * `@JvmOverloads` defaults (`backendApi`, `UserSession(app)`, `Dispatchers.IO`).
 */

package com.example.myapplication

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileUiState(
    val username: String = "",
    val email: String = "",
    val videosCount: Int? = null,
    val mistakesCount: Int? = null,
    val topMistakeTitle: String? = null,
    val videosThisWeek: Int? = null,
)

class ProfileViewModel @JvmOverloads constructor(
    app: Application,
    private val api: BackendAPI = backendApi,
    private val session: UserSession = UserSession(app),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val user = session.current.first() ?: return@launch
            _state.value = ProfileUiState(
                username = user.username,
                email = user.email,
            )
            val videos = try {
                withContext(ioDispatcher) { api.listMyVideos() }
            } catch (e: Exception) {
                Log.w("ProfileVM", "failed to load videos: ${e.message}")
                null
            }
            val mistakes = try {
                withContext(ioDispatcher) { api.listMyMistakes() }
            } catch (e: Exception) {
                Log.w("ProfileVM", "failed to load mistakes: ${e.message}")
                null
            }
            val topMistake = mistakes
                ?.groupingBy { it.title }
                ?.eachCount()
                ?.maxByOrNull { it.value }
                ?.key
            val cutoffWeek = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
            val thisWeek = videos?.count { v ->
                runCatching {
                    java.time.OffsetDateTime.parse(v.uploaded_at).toInstant().toEpochMilli() >= cutoffWeek
                }.getOrDefault(false)
            }
            _state.value = _state.value.copy(
                videosCount = videos?.size,
                mistakesCount = mistakes?.size,
                topMistakeTitle = topMistake,
                videosThisWeek = thisWeek,
            )
        }
    }
}