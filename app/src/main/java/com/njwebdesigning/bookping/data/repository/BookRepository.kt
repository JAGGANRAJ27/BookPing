
package com.njwebdesigning.bookping.data.repository

import android.content.Context
import com.njwebdesigning.bookping.data.local.BookDao
import com.njwebdesigning.bookping.data.local.BookEntity
import com.njwebdesigning.bookping.data.remote.OpacService
import com.njwebdesigning.bookping.notifications.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class BookRepository(
    private val opacService: OpacService,
    private val bookDao: BookDao
) {

    fun getActiveBooks(): Flow<List<BookEntity>> =
        bookDao.getActiveBooks()

    suspend fun syncBooks(
        context: Context,
        memberId: String,
        reminderDays: Int = 2,
        repeatIntervalHours: Int = 24
    ): Int {

        require(memberId.isNotBlank()) {
            "Member ID cannot be empty"
        }

        val previousBooks = bookDao.getActiveBooks().first()

        val remoteBooks = opacService.fetchBooks(memberId)
        val syncTime = System.currentTimeMillis()

        val localBooks = remoteBooks.map { book ->
            BookEntity(
                accessionNumber = book.accessionNumber,
                title = book.title,
                issueDateMillis = book.issueDateMillis,
                dueDateMillis = book.dueDateMillis,
                isReturned = false,
                lastSyncedAt = syncTime
            )
        }

        bookDao.syncActiveBooks(localBooks)

        val savedBooks = bookDao.getActiveBooks().first()

        if (savedBooks.size != localBooks.size) {
            error(
                "Room sync verification failed. " +
                        "OPAC: ${localBooks.size}, Room: ${savedBooks.size}"
            )
        }

        val currentAccessions =
            localBooks.map { it.accessionNumber }.toSet()

        previousBooks
            .filter { it.accessionNumber !in currentAccessions }
            .forEach { book ->
                ReminderScheduler.cancelReminder(
                    context,
                    book.accessionNumber
                )
            }

        localBooks.forEach { book ->
            ReminderScheduler.scheduleReminder(
                context = context,
                book = book,
                reminderDays = reminderDays,
                repeatIntervalHours = repeatIntervalHours
            )
        }

        return savedBooks.size
    }
}
