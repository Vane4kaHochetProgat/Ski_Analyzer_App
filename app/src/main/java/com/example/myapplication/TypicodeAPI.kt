package com.example.myapplication

import retrofit2.http.GET
import retrofit2.http.Path

interface TypicodeAPI {
    @GET("/todos/{n}")
    suspend fun fetch(@Path("n") n: Int) : String
}