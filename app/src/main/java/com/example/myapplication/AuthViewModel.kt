package com.example.myapplication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

sealed class AuthError {
    object InvalidCredentials : AuthError()
    object UserExists : AuthError()
    data class ServerError(val code: Int) : AuthError()
    data class NetworkError(val detail: String) : AuthError()
}

class AuthViewModel @JvmOverloads constructor(
    app: Application,
    private val api: BackendAPI = backendApi,
    private val session: UserSession = UserSession(app),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(app) {

    val current: StateFlow<StoredUser?> = session.current
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _errorMessage = MutableStateFlow<AuthError?>(null)
    val errorMessage: StateFlow<AuthError?> = _errorMessage.asStateFlow()

    fun submit(mode: AuthMode, username: String, email: String, password: String) {
        if (_isSubmitting.value) return
        _isSubmitting.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val user = withContext(ioDispatcher) {
                    when (mode) {
                        AuthMode.LOGIN ->
                            api.login(LoginRequest(email.trim(), password))
                        AuthMode.REGISTER ->
                            api.register(RegisterRequest(username.trim(), email.trim(), password))
                    }
                }
                session.save(user)
            } catch (e: HttpException) {
                _errorMessage.value = when (e.code()) {
                    401 -> AuthError.InvalidCredentials
                    409 -> AuthError.UserExists
                    else -> AuthError.ServerError(e.code())
                }
            } catch (e: Exception) {
                _errorMessage.value = AuthError.NetworkError(e.message ?: "unknown")
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { session.clear() }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}