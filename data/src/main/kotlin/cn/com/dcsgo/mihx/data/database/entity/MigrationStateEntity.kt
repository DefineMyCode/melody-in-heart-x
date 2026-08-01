package cn.com.dcsgo.mihx.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Key/value scratch space for one-off migration bookkeeping (e.g. "scanned_v1" -> "done"),
 * so migrations stay idempotent across app updates.
 */
@Entity(tableName = "migration_state")
data class MigrationStateEntity(
    @PrimaryKey val key: String,
    val value: String,
)
