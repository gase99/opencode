package com.confighub.app.data.net

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class SourceFetcherTest {

    private lateinit var server: MockWebServer
    private lateinit var fetcher: SourceFetcher

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        fetcher = SourceFetcher(OkHttpClient())
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    @Test
    fun `returns body, etag and code on 200`() = runBlocking<Unit> {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("ETag", "\"v1\"")
                .setHeader("Last-Modified", "Wed, 21 Oct 2026 07:28:00 GMT")
                .setBody("hello world"),
        )
        val outcome = fetcher.fetch(server.url("/x").toString(), null, null)
        val success = outcome as SourceFetcher.Outcome.Success
        assertThat(success.body).isEqualTo("hello world")
        assertThat(success.etag).isEqualTo("\"v1\"")
        assertThat(success.lastModified).contains("2026")
        assertThat(success.httpCode).isEqualTo(200)
    }

    @Test
    fun `conditional request sends headers and handles 304`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "\"v1\"").setBody("body1"))
        server.enqueue(MockResponse().setResponseCode(304))
        fetcher.fetch(server.url("/x").toString(), null, null)
        val second = fetcher.fetch(server.url("/x").toString(), "\"v1\"", null)
        assertThat(second).isEqualTo(SourceFetcher.Outcome.NotModified)

        assertThat(server.takeRequest().getHeader("If-None-Match")).isNull()
        assertThat(server.takeRequest().getHeader("If-None-Match")).isEqualTo("\"v1\"")
    }

    @Test
    fun `http error carries code and retry-after`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30"))
        val outcome = fetcher.fetch(server.url("/x").toString(), null, null)
        val error = outcome as SourceFetcher.Outcome.HttpError
        assertThat(error.code).isEqualTo(429)
        assertThat(error.retryAfterSeconds).isEqualTo(30)
    }

    @Test
    fun `connection failure yields sanitized Failure`() = runBlocking<Unit> {
        val dead = MockWebServer()
        dead.start()
        val url = dead.url("/x").toString()
        dead.shutdown()
        val outcome = fetcher.fetch(url, null, null)
        assertThat(outcome).isInstanceOf(SourceFetcher.Outcome.Failure::class.java)
    }

    @Test
    fun `sends honest user agent`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        fetcher.fetch(server.url("/x").toString(), null, null)
        assertThat(server.takeRequest().getHeader("User-Agent")).contains("ConfigHub")
    }
}

class RateLimiterTest {

    @Test
    fun `second call within interval delays`() {
        kotlinx.coroutines.runBlocking<Unit> {
            val limiter = RateLimiter()
            var calls = 0
            val start = System.nanoTime()
            limiter.withPermit("host", 150) { calls++ }
            limiter.withPermit("host", 150) { calls++ }
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            assertThat(calls).isEqualTo(2)
            assertThat(elapsedMs).isAtLeast(100)
        }
    }

    @Test
    fun `different hosts are independent`() {
        kotlinx.coroutines.runBlocking<Unit> {
            val limiter = RateLimiter()
            val start = System.nanoTime()
            limiter.withPermit("a", 5000) { }
            limiter.withPermit("b", 5000) { }
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            assertThat(elapsedMs).isLessThan(1000)
        }
    }
}
