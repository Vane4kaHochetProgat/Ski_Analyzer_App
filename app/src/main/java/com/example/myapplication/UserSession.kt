/**
 * Persistent storage for the signed-in user.
 *
 * Backed by Jetpack [DataStore Preferences][androidx.datastore.preferences],
 * registered as `Context.userSessionStore` with the file name `user_session`.
 * Stores `user_id`, `username`, `email` — no password or token, since the
 * backend is auth-less and the user id is passed explicitly on each request.
 *
 * API:
 *   * `current: Flow<StoredUser?>` — null when no user is signed in;
 *     `AuthViewModel.current` lifts this into a `StateFlow` and drives the
 *     auth-vs-main-app switch in [MainActivity].
 *   * `save(UserDto)`  — writes id/username/email after a successful login or
 *     registration response.
 *   * `clear()`        — wipes the prefs file on sign-out.
 */

package com.example.myapplication

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.userSessionStore by preferencesDataStore(name = "user_session")

private val KEY_USER_ID = intPreferencesKey("user_id")
private val KEY_USERNAME = stringPreferencesKey("username")
private val KEY_EMAIL = stringPreferencesKey("email")
private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
private val KEY_OFFLINE_MODE = stringPreferencesKey("offline_mode")

data class StoredUser(val userId: Int, val username: String, val email: String)

object TokenHolder {
    @Volatile private var token: String? = null
    fun get(): String? = token
    internal fun set(value: String?) { token = value }
}

enum class OfflineMode { RUN_LOCALLY, WAIT_FOR_INTERNET }

class UserSession(private val context: Context) {

    val current: Flow<StoredUser?> = context.userSessionStore.data.map { prefs ->
        val id = prefs[KEY_USER_ID] ?: return@map null
        StoredUser(
            userId = id,
            username = prefs[KEY_USERNAME].orEmpty(),
            email = prefs[KEY_EMAIL].orEmpty()
        )
    }

    val offlineMode: Flow<OfflineMode> = context.userSessionStore.data.map { prefs ->
        when (prefs[KEY_OFFLINE_MODE]) {
            OfflineMode.WAIT_FOR_INTERNET.name -> OfflineMode.WAIT_FOR_INTERNET
            else -> OfflineMode.RUN_LOCALLY
        }
    }

    suspend fun setOfflineMode(mode: OfflineMode) {
        context.userSessionStore.edit { prefs ->
            prefs[KEY_OFFLINE_MODE] = mode.name
        }
    }

    suspend fun save(user: UserDto, accessToken: String) {
        context.userSessionStore.edit { prefs ->
            prefs[KEY_USER_ID] = user.user_id
            prefs[KEY_USERNAME] = user.username
            prefs[KEY_EMAIL] = user.email
            prefs[KEY_ACCESS_TOKEN] = accessToken
        }
        TokenHolder.set(accessToken)
    }

    suspend fun clear() {
        // намеренно НЕ трогаем offline_mode — это устройство-специфичная настройка
        context.userSessionStore.edit { prefs ->
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_EMAIL)
            prefs.remove(KEY_ACCESS_TOKEN)
        }
        TokenHolder.set(null)
    }

    /** Один раз на старте приложения: заполнить [TokenHolder] из DataStore. */
    suspend fun bootstrap() {
        val prefs = context.userSessionStore.data.first()
        TokenHolder.set(prefs[KEY_ACCESS_TOKEN])
    }
}
