package com.confighub.app.di

import android.content.Context
import androidx.room.Room
import com.confighub.app.data.db.AppDatabase
import com.confighub.app.data.db.ConfigDao
import com.confighub.app.data.db.HealthDao
import com.confighub.app.data.db.SourceDao
import com.confighub.app.data.net.FetchPolicy
import com.confighub.app.data.net.SafeDns
import com.confighub.app.data.net.SourceFetcher
import com.confighub.app.data.security.SecretCrypto
import com.confighub.app.data.security.SecretStore
import com.confighub.app.domain.health.DefaultProbeSocketFactory
import com.confighub.app.domain.health.ProbeSocketFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context, secretStore: SecretStore): AppDatabase {
        val builder = Room.databaseBuilder(context, AppDatabase::class.java, "confighub.db")
        if (secretStore.secure) {
            runCatching { System.loadLibrary("sqlcipher") }.onSuccess {
                builder.openHelperFactory(
                    net.zetetic.database.sqlcipher.SupportOpenHelperFactory(secretStore.dbPassphrase()),
                )
            }
        }
        // If SQLCipher/Keystore is unavailable the DB falls back to unencrypted storage;
        // the UI surfaces the warning (SecretStore.FALLBACK_WARNING). See docs/KNOWN_LIMITATIONS.md.
        return builder.build()
    }

    @Provides
    fun provideSourceDao(db: AppDatabase): SourceDao = db.sourceDao()

    @Provides
    fun provideConfigDao(db: AppDatabase): ConfigDao = db.configDao()

    @Provides
    fun provideHealthDao(db: AppDatabase): HealthDao = db.healthDao()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .dns(SafeDns)
        .build()

    @Provides
    @Singleton
    fun provideSourceFetcher(client: OkHttpClient): SourceFetcher = SourceFetcher(client)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CryptoModule {
    @Binds
    @Singleton
    abstract fun bindSecretCrypto(impl: SecretStore): SecretCrypto
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthModule {
    @Binds
    @Singleton
    abstract fun bindProbeSocketFactory(impl: DefaultProbeSocketFactory): ProbeSocketFactory
}

@Module
@InstallIn(SingletonComponent::class)
object PipelineModule {
    @Provides
    @Singleton
    fun provideFetchPolicy(): FetchPolicy = FetchPolicy()
}
