/**
 * JVM unit tests для [AuthInterceptor].
 *
 * Покрытие:
 *   * токен присутствует в TokenHolder → заголовок Authorization добавляется как Bearer ;
 *   * токен отсутствует → запрос уходит без Authorization;
 *   * заголовок Authorization уже задан вручную → интерсептор не перетирает.
 *
 * Используем MockWebServer вместо мокинга OkHttp Chain — это проще и заодно
 * проверяет реальное поведение в обёртке клиента.
 */

package com.vane4ka.skianalyzer

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    private fun client(token: String?): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenProvider = { token }))
            .build()

    @Test
    fun `adds Bearer header when token present`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val resp = client("abc123").newCall(
            Request.Builder().url(server.url("/x")).build()
        ).execute()
        resp.close()
        val recorded = server.takeRequest()
        assertEquals("Bearer abc123", recorded.getHeader("Authorization"))
    }

    @Test
    fun `no Authorization header when token is null`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val resp = client(null).newCall(
            Request.Builder().url(server.url("/x")).build()
        ).execute()
        resp.close()
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `does not overwrite explicit Authorization`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val resp = client("abc123").newCall(
            Request.Builder()
                .url(server.url("/x"))
                .header("Authorization", "Custom xyz")
                .build()
        ).execute()
        resp.close()
        val recorded = server.takeRequest()
        assertEquals("Custom xyz", recorded.getHeader("Authorization"))
    }
}
