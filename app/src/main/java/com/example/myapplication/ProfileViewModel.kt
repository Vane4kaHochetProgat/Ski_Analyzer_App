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
    val videosCount: Int? = null
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
                videosCount = null
            )
            val count = try {
                withContext(ioDispatcher) { api.listVideos(user.userId).size }
            } catch (e: Exception) {
                Log.w("ProfileVM", "failed to load videos: ${e.message}")
                null
            }
            _state.value = _state.value.copy(videosCount = count)
        }
    }
}