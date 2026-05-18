package com.example.myapplication

import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MyAppViewModel : ViewModel() {

    //
    val state: MutableState<Int> = mutableStateOf(1)
    val uriState: MutableState<Uri?> = mutableStateOf(null)
    val cache = mutableStateMapOf<Int, String>()

    fun request(n: Int) {
        if (cache.containsKey(n)) {
            return
        }
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                typicode.fetch(n)
            }
            cache[n] = text
        }
    }
}