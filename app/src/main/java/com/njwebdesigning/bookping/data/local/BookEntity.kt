package com.njwebdesigning.bookping.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(

    @PrimaryKey
    val accessionNumber: String,

    val title: String,
    val issueDateMillis: Long,
    val dueDateMillis: Long,

    val isReturned: Boolean = false,
    val lastSyncedAt: Long = System.currentTimeMillis()
)