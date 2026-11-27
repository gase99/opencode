package com.confighub.app.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.confighub.app.core.security.SafeUrlValidator
import com.confighub.app.data.db.AppDatabase
import com.confighub.app.data.db.FetchStatus
import com.confighub.app.data.db.SourceType
import com.confighub.app.data.net.FetchPolicy
import com.confighub.app.data.net.RateLimiter
import com.confighub.app.data.net.SourceFetcher
import com.confighub.app.data.security.FieldCipher
import com.confighub.app.data.security.SecretCrypto
import com.confighub.app.data.settings.AppSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Base64

/**
 * End-to-end pipeline test over an in-memory Room database and a MockWebServer.
 *
 * NOTE: SQLCipher and AndroidKeyStore cannot run on the JVM; the repository logic is
 * exercised with a test [SecretCrypto] (plaintext fallback mode). Real-device encryption
 * is verified manually per docs/RELEASE_CHECKLIST.md.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SourceRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var repo: SourceRepository

    private val permissive = UrlPolicy { SafeUrlValidator.Result.Ok }

    private val testCrypto = object : SecretCrypto {
        override val secure = false
        override fun encryptField(plain: ByteArray): Pair<ByteArray?, ByteArray> = null to plain
        override fun decryptField(iv: ByteArray?, data: ByteArray): ByteArray = data
        override fun dbPassphrase(): ByteArray = ByteArray(32)
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = SourceRepository(
            db = db,
            fetcher = SourceFetcher(OkHttpClient()),
            fieldCipher = FieldCipher(testCrypto),
            rateLimiter = RateLimiter(),
            fetchPolicy = FetchPolicy(minHostIntervalMs = 10),
        )
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
        db.close()
    }

    private fun b64(payload: String): String = Base64.getEncoder().encodeToString(payload.toByteArray())

    private suspend fun addSource(url: String): com.confighub.app.data.db.SourceEntity {
        val result = repo.addSource("Test source", url, SourceType.SUBSCRIPTION_URL, "license note", 6, permissive)
        return result.getOrThrow()
    }

    @Test
    fun `end-to-end fetch parse validate dedup store`() = runBlocking<Unit> {
        val vless = "vless://b831381d-6324-4d53-ad4f-8cda48b30811@a.example.com:443#a1"
        val vlessDup = "vless://b831381d-6324-4d53-ad4f-8cda48b30811@a.example.com:443#a2-different-tag"
        val trojan = "trojan://pw@b.example.com:443#b1"
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("ETag", "\"v1\"")
                .setBody(b64(listOf(vless, vlessDup, trojan).joinToString("\n"))),
        )
        val source = addSource(server.url("/sub").toString())
        val updated = repo.refreshSource(source, AppSettings(), permissive)

        assertThat(updated.lastStatus).isEqualTo(FetchStatus.OK)
        assertThat(updated.lastItemCount).isEqualTo(2)
        assertThat(updated.etag).isEqualTo("\"v1\"")
        assertThat(updated.lastError).isNull()

        val configs = db.configDao().observeAll().first()
        assertThat(configs).hasSize(2)
        assertThat(configs.map { it.protocol.name }).containsExactly("VLESS", "TROJAN")
        // Dedup identity kept in hash; favorites field default false
        assertThat(configs.all { !it.isFavorite }).isTrue()
    }

    @Test
    fun `second refresh with same etag yields NOT_MODIFIED`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "\"v1\"").setBody(b64("vless://b831381d-6324-4d53-ad4f-8cda48b30811@a.example.com:443#a")))
        server.enqueue(MockResponse().setResponseCode(304))
        val source = addSource(server.url("/sub").toString())
        val first = repo.refreshSource(source, AppSettings(), permissive)
        assertThat(first.lastStatus).isEqualTo(FetchStatus.OK)

        val second = repo.refreshSource(first, AppSettings(), permissive)
        assertThat(second.lastStatus).isEqualTo(FetchStatus.NOT_MODIFIED)
        assertThat(db.configDao().observeAll().first()).hasSize(1)
    }

    @Test
    fun `429 recorded as ERROR_HTTP`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60"))
        val source = addSource(server.url("/sub").toString())
        val updated = repo.refreshSource(source, AppSettings(), permissive)
        assertThat(updated.lastStatus).isEqualTo(FetchStatus.ERROR_HTTP)
        assertThat(updated.lastHttpCode).isEqualTo(429)
        assertThat(updated.lastError).contains("429")
    }

    @Test
    fun `malformed lines are ignored, valid ones stored`() = runBlocking<Unit> {
        val body = """
            not a uri at all
            ${'$'}{'\n'}
            vless://b831381d-6324-4d53-ad4f-8cda48b30811@ok.example.com:443#fine
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val source = addSource(server.url("/sub").toString())
        val updated = repo.refreshSource(source, AppSettings(), permissive)
        assertThat(updated.lastItemCount).isEqualTo(1)
    }

    @Test
    fun `experimental protocols gated by settings`() = runBlocking<Unit> {
        val payload = "hysteria2://pw@h.example.com:443#hy2\n" +
            "vless://b831381d-6324-4d53-ad4f-8cda48b30811@v.example.com:443#v"
        server.enqueue(MockResponse().setResponseCode(200).setBody(payload))
        val source = addSource(server.url("/strict").toString())
        val strict = repo.refreshSource(source, AppSettings(), permissive)
        assertThat(strict.lastItemCount).isEqualTo(1) // only vless

        server.enqueue(MockResponse().setResponseCode(200).setBody(payload))
        val lenient = repo.refreshSource(
            strict.copy(lastStatus = FetchStatus.NEVER, etag = null),
            AppSettings(allowExperimentalProtocols = true),
            permissive,
        )
        assertThat(lenient.lastItemCount).isEqualTo(2) // vless + hysteria2
    }

    @Test
    fun `private url source rejected at add`() = runBlocking<Unit> {
        val result = repo.addSource(
            "bad",
            "http://192.168.1.5/configs",
            SourceType.CUSTOM,
            null,
            6,
            UrlPolicy.fromSettings(allowInsecureHttp = true),
        )
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `deleteSource removes its configs too`() = runBlocking<Unit> {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(b64("vless://b831381d-6324-4d53-ad4f-8cda48b30811@a.example.com:443#a")),
        )
        val source = addSource(server.url("/sub").toString())
        repo.refreshSource(source, AppSettings(), permissive)
        assertThat(db.configDao().observeAll().first()).hasSize(1)

        repo.deleteSource(source.id)
        assertThat(db.configDao().observeAll().first()).isEmpty()
        assertThat(repo.observeSources().first()).isEmpty()
    }
}
