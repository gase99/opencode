package com.confighub.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {

    @Query("SELECT * FROM sources ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun getById(id: Long): SourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: SourceEntity): Long

    @Update
    suspend fun update(source: SourceEntity)

    @Query("UPDATE sources SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ConfigDao {

    @Query("SELECT * FROM configs ORDER BY lastSeenAt DESC")
    fun observeAll(): Flow<List<ConfigEntity>>

    @Query("SELECT * FROM configs WHERE hash = :hash")
    fun observeByHash(hash: String): Flow<ConfigEntity?>

    @Query("SELECT * FROM configs WHERE hash = :hash")
    suspend fun getByHash(hash: String): ConfigEntity?

    @Query("SELECT * FROM configs WHERE hash IN (:hashes)")
    suspend fun getByHashes(hashes: List<String>): List<ConfigEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ConfigEntity>)

    @Query("UPDATE configs SET lastSeenAt = :at WHERE hash IN (:hashes)")
    suspend fun touch(hashes: List<String>, at: Long)

    @Query("UPDATE configs SET isFavorite = :favorite WHERE hash = :hash")
    suspend fun setFavorite(hash: String, favorite: Boolean)

    @Query("UPDATE configs SET lastScore = :score, lastScoredAt = :scoredAt, lastTestAt = :testedAt WHERE hash = :hash")
    suspend fun updateScore(hash: String, score: Float, scoredAt: Long, testedAt: Long)

    @Query("DELETE FROM configs WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long)

    @Query("SELECT COUNT(*) FROM configs WHERE sourceId = :sourceId")
    suspend fun countBySource(sourceId: Long): Int

    @Query("SELECT * FROM configs WHERE enabled = 1 ORDER BY lastTestAt ASC LIMIT :limit")
    suspend fun dueForTest(limit: Int): List<ConfigEntity>
}

@Dao
interface HealthDao {

    @Insert
    suspend fun insert(sample: HealthSampleEntity): Long

    @Query("SELECT * FROM health_samples WHERE configHash = :hash ORDER BY testedAt DESC LIMIT :limit")
    suspend fun forConfig(hash: String, limit: Int): List<HealthSampleEntity>

    @Query("SELECT * FROM health_samples WHERE configHash = :hash ORDER BY testedAt DESC LIMIT :limit")
    fun observeForConfig(hash: String, limit: Int): Flow<List<HealthSampleEntity>>

    /** Newest sample per config — used for the Home list. */
    @Query(
        "SELECT * FROM health_samples WHERE id IN " +
            "(SELECT MAX(id) FROM health_samples GROUP BY configHash)",
    )
    fun observeLatestPerConfig(): Flow<List<HealthSampleEntity>>

    @Query(
        "SELECT * FROM health_samples WHERE id IN " +
            "(SELECT MAX(id) FROM health_samples GROUP BY configHash)",
    )
    suspend fun latestPerConfigOnce(): List<HealthSampleEntity>

    @Query("SELECT COUNT(*) FROM health_samples WHERE configHash = :hash AND ok = 1")
    suspend fun successCount(hash: String): Int

    @Query("SELECT COUNT(*) FROM health_samples WHERE configHash = :hash")
    suspend fun totalCount(hash: String): Int

    @Query("DELETE FROM health_samples WHERE testedAt < :olderThan")
    suspend fun pruneOlderThan(olderThan: Long)
}
