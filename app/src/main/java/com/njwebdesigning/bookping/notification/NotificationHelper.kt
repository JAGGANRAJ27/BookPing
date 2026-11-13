package com.njwebdesigning.bookping.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.njwebdesigning.bookping.MainActivity

object NotificationHelper {

    private const val CHANNEL_ID = "bookping_return_reminders"
    private const val CHANNEL_NAME = "Book Return Reminders"

    fun createNotificationChannel(context: Context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Persistent reminders for borrowed library books"
                setShowBadge(true)
            }

            val manager = context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

            manager.createNotificationChannel(channel)
        }
    }

    fun showReturnReminder(
        context: Context,
        accessionNumber: String,
        bookTitle: String,
        dueDate: String
    ) {

        // Android 13+ notification permission check
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationIntent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            accessionNumber.hashCode(),
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context,
            CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Book return reminder")
            .setContentText("Return \"$bookTitle\" by $dueDate")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "$bookTitle\n" +
                                "Accession Number: $accessionNumber\n" +
                                "Due date: $dueDate\n\n" +
                                "Please return your book to KIOT Library."
                    )
            )
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                accessionNumber.hashCode(),
                notification
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun cancelReminder(
        context: Context,
        accessionNumber: String
    ) {
        NotificationManagerCompat.from(context).cancel(
            accessionNumber.hashCode()
        )
    }
}
