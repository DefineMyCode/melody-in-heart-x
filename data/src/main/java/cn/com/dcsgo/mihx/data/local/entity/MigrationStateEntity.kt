package cn.com.dcsgo.mihx.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "migration_state")
data class MigrationStateEntity(
    @PrimaryKey val name: String,
    val completedAt: Long,
)
