
package com.njwebdesigning.bookping.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.njwebdesigning.bookping.data.local.BookEntity
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    fun scheduleReminder(
        context: Context,
        book: BookEntity,
        reminderDays: Int = 2,
        repeatIntervalHours: Int = 24
    ) {
        if (book.isReturned) {
            cancelReminder(context, book.accessionNumber)
            return
        }

        require(reminderDays in 0..30)
        require(repeatIntervalHours in 1..24)

        val manager = WorkManager.getInstance(context)

        manager.cancelUniqueWork(initialWorkName(book.accessionNumber))
        manager.cancelUniqueWork(repeatWorkName(book.accessionNumber))

        val reminderTime = book.dueDateMillis -
                TimeUnit.DAYS.toMillis(reminderDays.toLong())

        val delay = (reminderTime - System.currentTimeMillis())
            .coerceAtLeast(0L)

        val request = OneTimeWorkRequestBuilder<BookReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    BookReminderWorker.KEY_ACCESSION to book.accessionNumber,
                    BookReminderWorker.KEY_REPEAT to false,
                    BookReminderWorker.KEY_REPEAT_INTERVAL_HOURS to
                            repeatIntervalHours
                )
            )
            .build()

        manager.enqueueUniqueWork(
            initialWorkName(book.accessionNumber),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelReminder(context: Context, accessionNumber: String) {
        val manager = WorkManager.getInstance(context)

        manager.cancelUniqueWork(initialWorkName(accessionNumber))
        manager.cancelUniqueWork(repeatWorkName(accessionNumber))

        NotificationHelper.cancelReminder(context, accessionNumber)
    }

    private fun initialWorkName(accession: String) =
        "bookping_initial_$accession"

    fun repeatWorkName(accession: String) =
        "bookping_repeat_$accession"
}
