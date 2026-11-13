
package com.njwebdesigning.bookping.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.workDataOf
import com.njwebdesigning.bookping.data.local.BookDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class BookReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val accession = inputData.getString(KEY_ACCESSION)
            ?: return Result.failure()

        val dao = BookDatabase
            .getDatabase(applicationContext)
            .bookDao()

        val book = dao.getBookByAccession(accession)

        if (book == null || book.isReturned) {
            NotificationHelper.cancelReminder(applicationContext, accession)

            WorkManager.getInstance(applicationContext)
                .cancelUniqueWork(
                    ReminderScheduler.repeatWorkName(accession)
                )

            return Result.success()
        }

        NotificationHelper.createNotificationChannel(applicationContext)

        val formattedDueDate = SimpleDateFormat(
            "dd MMMM yyyy",
            Locale.getDefault()
        ).format(Date(book.dueDateMillis))

        NotificationHelper.showReturnReminder(
            context = applicationContext,
            accessionNumber = book.accessionNumber,
            bookTitle = book.title,
            dueDate = formattedDueDate
        )

        if (!inputData.getBoolean(KEY_REPEAT, false)) {
            val intervalHours = inputData.getInt(
                KEY_REPEAT_INTERVAL_HOURS,
                24
            ).coerceIn(1, 24)

            val repeatRequest =
                PeriodicWorkRequestBuilder<BookReminderWorker>(
                    intervalHours.toLong(),
                    TimeUnit.HOURS
                )
                    .setInputData(
                        workDataOf(
                            KEY_ACCESSION to accession,
                            KEY_REPEAT to true,
                            KEY_REPEAT_INTERVAL_HOURS to intervalHours
                        )
                    )
                    .build()

            WorkManager.getInstance(applicationContext)
                .enqueueUniquePeriodicWork(
                    ReminderScheduler.repeatWorkName(accession),
                    ExistingPeriodicWorkPolicy.REPLACE,
                    repeatRequest
                )
        }

        return Result.success()
    }

    companion object {
        const val KEY_ACCESSION = "accession_number"
        const val KEY_REPEAT = "repeat_reminder"
        const val KEY_REPEAT_INTERVAL_HOURS =
            "repeat_interval_hours"
    }
}
