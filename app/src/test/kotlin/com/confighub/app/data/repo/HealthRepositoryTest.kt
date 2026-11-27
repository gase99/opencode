package com.confighub.app.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.confighub.app.data.db.AppDatabase
import com.confighub.app.data.db.ConfigEntity
import com.confighub.app.data.db.HealthSampleEntity
import com.confighub.app.data.db.HealthDao
import com.confighub.app.data.security.FieldCipher
import com.confighub.app.data.security.SecretCrypto
import com.confighub.app.data.settings.SettingsRepository
import com.confighub.app.core.model.Protocol
import com.confighub.app.core.model.Transport
import com.confighub.app.core.model.TlsMode
import com.confighub.app.domain.health.FakeProbeSocketFactory
import com.confighub.app.domain.health.FakeDnsResolver
import com.confighub.app.domain.health.HealthChecker
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * HealthRepository pipeline test with deterministic fake sockets/DNS.
 * (SQLCipher/Keystore are unavailable on the JVM — plaintext test crypto is used.)
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HealthRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: HealthRepository

    private val testCrypto = object : SecretCrypto {
        override val secure = false
        override fun encryptField(plain: ByteArray): Pair<ByteArray?, ByteArray> = null to plain
        override fun decryptField(iv: ByteArray?, data: ByteArray): ByteArray = data
        override fun dbPassphrase(): ByteArray = ByteArray(32)
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val checker = HealthChecker(
            socketFactory = FakeProbeSocketFactory(tcpLatencies = listOf(120, 140, 130)),
            dnsResolver = FakeDnsResolver(),
        )
        repo = HealthRepository(
            db = db,
            healthChecker = checker,
            fieldCipher = FieldCipher(testCrypto),
            operatorDetector = com.confighub.app.domain.operator.OperatorDetector(context),
            settingsRepository = SettingsRepository(context),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun configEntity(hash: String = "abc123"): ConfigEntity = ConfigEntity(
        hash = hash,
        protocol = Protocol.VLESS,
        host = "example.com",
        port = 443,
        network = Transport.TCP,
        tls = TlsMode.NONE,
        sourceId = 1,
        firstSeenAt = 0,
        lastSeenAt = 0,
        secretIv = null,
        secretEnc = "secret-pass".toByteArray(),
    )

    @Test
    fun `testNow stores sample and score`() = runBlocking<Unit> {
        db.configDao().insertAll(listOf(configEntity()))
        val result = repo.testNow("abc123")
        assertThat(result).isNotNull()
        assertThat(result!!.ok).isTrue()
        assertThat(result.tcpMeanMs).isEqualTo(130)

        val samples = db.healthDao().forConfig("abc123", 10)
        assertThat(samples).hasSize(1)
        assertThat(samples[0].ok).isTrue()
        assertThat(samples[0].errorKind).isNull()

        val updated = db.configDao().getByHash("abc123")!!
        assertThat(updated.lastScore).isNotNull()
        assertThat(updated.lastTestAt).isNotNull()
    }

    @Test
    fun `missing config returns null`() = runBlocking<Unit> {
        assertThat(repo.testNow("does-not-exist")).isNull()
    }

    @Test
    fun `score with empty history is neutral`() = runBlocking<Unit> {
        val entity = configEntity()
        val score = repo.scoreFor(entity, com.confighub.app.data.settings.AppSettings(), com.confighub.app.domain.operator.OperatorId.OTHER)
        assertThat(score.total).isIn(0..100)
    }

    @Test
    fun `prune removes old samples`() = runBlocking<Unit> {
        db.healthDao().insert(
            HealthSampleEntity(
                configHash = "abc123",
                testedAt = System.currentTimeMillis() - 5L * 24 * 3_600_000,
                ok = true,
                networkType = "WIFI",
                operatorCode = null,
            ),
        )
        repo.prune(keepDays = 1)
        assertThat(db.healthDao().forConfig("abc123", 10)).isEmpty()
    }
}

/** Fake implementations for deterministic tests (see HealthCheckerTest for more). */
object Fakes {
    fun healthDao(db: AppDatabase): HealthDao = db.healthDao()
}
