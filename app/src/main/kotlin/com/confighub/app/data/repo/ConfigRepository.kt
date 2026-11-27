package com.confighub.app.data.repo

import com.confighub.app.data.db.AppDatabase
import com.confighub.app.data.db.ConfigEntity
import com.confighub.app.data.security.FieldCipher
import com.confighub.app.data.db.toConfigProfile
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Read/write access to cached configs and favorites. */
@Singleton
class ConfigRepository @Inject constructor(
    private val db: AppDatabase,
    private val fieldCipher: FieldCipher,
) {

    fun observeConfigs(): Flow<List<ConfigEntity>> = db.configDao().observeAll()

    fun observeConfig(hash: String): Flow<ConfigEntity?> = db.configDao().observeByHash(hash)

    suspend fun getConfigOnce(hash: String): ConfigEntity? = db.configDao().getByHash(hash)

    suspend fun toggleFavorite(hash: String, favorite: Boolean) {
        db.configDao().setFavorite(hash, favorite)
    }

    /** Oldest-tested enabled configs first (SQLite orders NULLs first with ASC). */
    suspend fun dueForTest(limit: Int): List<ConfigEntity> = db.configDao().dueForTest(limit)

    /** Decrypts and rebuilds the connect-time profile; returns null on decryption failure. */
    suspend fun resolveProfile(entity: ConfigEntity): ConfigProfile? = entity.toConfigProfile(fieldCipher)
}
