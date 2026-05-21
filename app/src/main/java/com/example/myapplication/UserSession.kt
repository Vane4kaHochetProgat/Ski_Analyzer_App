package com.example.myapplication

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.userSessionStore by preferencesDataStore(name = "user_session")

private val KEY_USER_ID = intPreferencesKey("user_id")
private val KEY_USERNAME = stringPreferencesKey("username")
private val KEY_EMAIL = stringPreferencesKey("email")

data class StoredUser(val userId: Int, val username: String, val email: String)

class UserSession(private val context: Context) {

    val current: Flow<StoredUser?> = context.userSessionStore.data.map { prefs ->
        val id = prefs[KEY_USER_ID] ?: return@map null
        StoredUser(
            userId = id,
            username = prefs[KEY_USERNAME].orEmpty(),
            email = prefs[KEY_EMAIL].orEmpty()
        )
    }

    suspend fun save(user: UserDto) {
        context.userSessionStore.edit { prefs ->
            prefs[KEY_USER_ID] = user.user_id
            prefs[KEY_USERNAME] = user.username
            prefs[KEY_EMAIL] = user.email
        }
    }

    suspend fun clear() {
        context.userSessionStore.edit { it.clear() }
    }
}
