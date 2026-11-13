
package com.njwebdesigning.bookping.notifications

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun NotificationTestButton() {

    val context = LocalContext.current

    Button(
        onClick = {
            NotificationHelper.showReturnReminder(
                context = context,
                accessionNumber = "8090",
                bookTitle = "THE ESSENTIAL GUIDE TO USER INTERFACE DESIGN",
                dueDate = "16 November 2026"
            )
        }
    ) {
        Text("Test persistent notification")
    }
}
