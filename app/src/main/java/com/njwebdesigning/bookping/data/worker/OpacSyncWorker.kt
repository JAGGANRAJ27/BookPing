
package com.njwebdesigning.bookping.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.njwebdesigning.bookping.data.local.BookDatabase
import com.njwebdesigning.bookping.data.preferences.UserPreferences
import com.njwebdesigning.bookping.data.remote.OpacService
import com.njwebdesigning.bookping.data.repository.BookRepository
import kotlinx.coroutines.flow.first

class OpacSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Read the saved KIOT member ID.
            val preferences = UserPreferences(applicationContext)
            val memberId = preferences.memberId.first()

            if (memberId.isBlank()) {
                return Result.success()
            }

            // Initialize Room and repository.
            val database =
                BookDatabase.getDatabase(applicationContext)

            val repository = BookRepository(
                opacService = OpacService(),
                bookDao = database.bookDao()
            )

            // Synchronize OPAC data with Room.
            repository.syncBooks(
                context = applicationContext,
                memberId = memberId,
                reminderDays = 2
            )

            Result.success()

        } catch (e: Exception) {
            Result.retry()
        }
    }
}
