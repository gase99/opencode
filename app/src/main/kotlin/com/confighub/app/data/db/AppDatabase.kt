package com.confighub.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Local cache database.
 *
 * On production devices the database file is encrypted with SQLCipher
 * (key wrapped by Android Keystore — see `data.security.SecretStore`).
 */
@Database(
    entities = [SourceEntity::class, ConfigEntity::class, HealthSampleEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun configDao(): ConfigDao
    abstract fun healthDao(): HealthDao
}
