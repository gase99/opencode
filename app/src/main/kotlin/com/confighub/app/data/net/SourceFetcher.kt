package com.confighub.app.data.net

import com.confighub.app.core.security.SecretMasker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Conditional HTTP fetcher for public sources.
 *
 * * sends `If-None-Match` / `If-Modified-Since` when previous state is available,
 * * reports `Retry-After` on 429/503,
 * * enforces a hard payload size cap,
 * * sends an honest, self-identifying User-Agent,
 * * runs on OkHttp client with [SafeDns] (SSRF protection).
 *
 * Hilt-friendly: only the OkHttpClient is injected; the size cap is a constant.
 */
class SourceFetcher @Inject constructor(
    private val client: OkHttpClient,
) {

    private val maxBytes: Long = DEFAULT_MAX_BYTES

    sealed class Outcome {
        data class Success(
            val body: String,
            val etag: String?,
            val lastModified: String?,
            val bytes: Int,
            val httpCode: Int,
        ) : Outcome()

        data object NotModified : Outcome()
        data class HttpError(val code: Int, val retryAfterSeconds: Long?) : Outcome()
        data class Failure(val message: String) : Outcome()
    }

    suspend fun fetch(url: String, etag: String?, lastModified: String?): Outcome =
        withContext(Dispatchers.IO) {
            val request = buildRequest(url, etag, lastModified)
            val outcome = runCatching {
                client.newCall(request).execute().use { response -> evaluate(response) }
            }
            outcome.getOrElse { throwable ->
                Outcome.Failure(SecretMasker.sanitize(throwable.message ?: throwable.javaClass.simpleName))
            }
        }

    private fun buildRequest(url: String, etag: String?, lastModified: String?): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
        etag?.let { builder.header("If-None-Match", it) }
        lastModified?.let { builder.header("If-Modified-Since", it) }
        return builder.build()
    }

    private fun evaluate(response: Response): Outcome {
        if (response.code == 304) return Outcome.NotModified
        if (!response.isSuccessful) {
            return Outcome.HttpError(response.code, response.header("Retry-After")?.toLongOrNull())
        }
        val body = response.body ?: return Outcome.Failure("empty response body")
        val declaredLength = body.contentLength()
        if (declaredLength > maxBytes) {
            return Outcome.Failure("payload too large: $declaredLength bytes (cap $maxBytes)")
        }
        val initialSize = if (declaredLength in 1..MAX_BUFFER_INIT) declaredLength.toInt() else MAX_BUFFER_INIT
        val buffer = ByteArrayOutputStream(initialSize)
        var total = 0
        body.byteStream().use { input ->
            val chunk = ByteArray(8192)
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                total += read
                if (total > maxBytes) {
                    return Outcome.Failure("payload exceeds cap of $maxBytes bytes")
                }
                buffer.write(chunk, 0, read)
            }
        }
        return Outcome.Success(
            body = String(buffer.toByteArray(), Charsets.UTF_8),
            etag = response.header("ETag"),
            lastModified = response.header("Last-Modified"),
            bytes = total,
            httpCode = response.code,
        )
    }

    companion object {
        const val USER_AGENT = "ConfigHub/0.1 (+https://github.com/gase99/opencode)"
        const val DEFAULT_MAX_BYTES = 5L * 1024 * 1024
        private const val MAX_BUFFER_INIT = 64 * 1024
    }
}
