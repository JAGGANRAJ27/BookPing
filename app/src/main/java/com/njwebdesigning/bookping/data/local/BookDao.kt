package com.njwebdesigning.bookping.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Upsert
    suspend fun upsertBook(book: BookEntity)

    @Upsert
    suspend fun upsertBooks(books: List<BookEntity>)

    @Query("""
        SELECT * FROM books
        ORDER BY dueDateMillis ASC
    """)
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("""
        SELECT * FROM books
        WHERE isReturned = 0
        ORDER BY dueDateMillis ASC
    """)
    fun getActiveBooks(): Flow<List<BookEntity>>

    @Query("""
        SELECT * FROM books
        WHERE accessionNumber = :accessionNumber
        LIMIT 1
    """)
    suspend fun getBookByAccession(
        accessionNumber: String
    ): BookEntity?

    @Query("""
        UPDATE books
        SET isReturned = 1
        WHERE accessionNumber = :accessionNumber
    """)
    suspend fun markAsReturned(accessionNumber: String)

    @Query("""
        UPDATE books
        SET isReturned = 1
        WHERE isReturned = 0
        AND accessionNumber NOT IN (:activeAccessionNumbers)
    """)
    suspend fun markMissingAsReturned(
        activeAccessionNumbers: List<String>
    )

    @Query("""
        UPDATE books
        SET isReturned = 1
        WHERE isReturned = 0
    """)
    suspend fun markAllAsReturned()

    @Transaction
    suspend fun syncActiveBooks(books: List<BookEntity>) {

        if (books.isEmpty()) {
            markAllAsReturned()
            return
        }

        upsertBooks(books)

        markMissingAsReturned(
            books.map { it.accessionNumber }
        )
    }
}