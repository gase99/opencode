package com.confighub.app.data.diagnostics

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.confighub.app.data.db.AppDatabase
import com.confighub.app.data.db.ConfigEntity
import com.confighub.app.data.db.SourceEntity
import com.confighub.app.data.db.SourceType
import com.confighub.app.data.security.SecretCrypto
import com.confighub.app.core.model.Protocol
import com.confighub.app.core.model.Transport
import com.confighub.app.core.model.TlsMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DiagnosticsExporterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var exporter: DiagnosticsExporter

    private val secretUuid = "b831381d-6324-4d53-ad4f-8cda48b30811"
    private val secretPassword = "super-secret-pass-42"

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
        exporter = DiagnosticsExporter(
            context = context,
            db = db,
            settingsRepository = com.confighub.app.data.settings.SettingsRepository(context),
            operatorDetector = com.confighub.app.domain.operator.OperatorDetector(context),
            secretCrypto = testCrypto,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `report contains metadata but never secrets`() = runBlocking<Unit> {
        db.sourceDao().insert(
            SourceEntity(
                name = "Example source",
                url = "https://sub.example.com/api/AbcDef123456789012345/configs?token=zzz-secret-token",
                type = SourceType.SUBSCRIPTION_URL,
                createdAt = 0,
                lastStatus = com.confighub.app.data.db.FetchStatus.OK,
                lastItemCount = 1,
            ),
        )
        db.configDao().insertAll(
            listOf(
                ConfigEntity(
                    hash = "hash0001",
                    protocol = Protocol.VMESS,
                    host = "node.example.com",
                    port = 443,
                    network = Transport.TCP,
                    tls = TlsMode.TLS,
                    sourceId = 1,
                    firstSeenAt = 0,
                    lastSeenAt = 0,
                    secretIv = null,
                    secretEnc = secretUuid.toByteArray(),
                ),
            ),
        )

        val report = exporter.buildReport()

        assertThat(report).contains("ConfigHub diagnostics")
        assertThat(report).contains("sub.example.com") // host survives redaction
        assertThat(report).contains("node.example.com")
        assertThat(report).contains("VMESS")

        // Secrets never appear.
        assertThat(report.contains(secretUuid)).isFalse()
        assertThat(report.contains(secretPassword)).isFalse()
        // Token-like path segment and query are redacted.
        assertThat(report.contains("AbcDef123456789012345")).isFalse()
        assertThat(report.contains("zzz-secret-token")).isFalse()
    }

    @Test
    fun `exportToFile writes the report to cache`() = runBlocking<Unit> {
        val file = exporter.exportToFile()
        assertThat(file.exists()).isTrue()
        assertThat(file.readText()).contains("ConfigHub diagnostics")
    }
}
