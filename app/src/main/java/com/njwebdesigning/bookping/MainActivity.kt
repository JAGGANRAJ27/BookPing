
package com.njwebdesigning.bookping

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.njwebdesigning.bookping.data.local.BookDatabase
import com.njwebdesigning.bookping.data.local.BookEntity
import com.njwebdesigning.bookping.data.preferences.UserPreferences
import com.njwebdesigning.bookping.data.remote.OpacService
import com.njwebdesigning.bookping.data.repository.BookRepository
import com.njwebdesigning.bookping.notifications.NotificationHelper
import com.njwebdesigning.bookping.notifications.NotificationTestButton
import com.njwebdesigning.bookping.notifications.ReminderScheduler
import com.njwebdesigning.bookping.data.worker.OpacSyncScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ----------------------------------------------------
// THEME COLORS
// ----------------------------------------------------

private val Blue = Color(0xFF2878E5)
private val Background = Color(0xFFF6F8FC)
private val Dark = Color(0xFF202B3C)
private val Muted = Color(0xFF758195)

// ----------------------------------------------------
// MAIN ACTIVITY
// ----------------------------------------------------

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        OpacSyncScheduler.schedule(this)

        setContent {
            MaterialTheme {
                NotificationPermissionHandler()
                BookPingApp()
            }
        }
    }
}

// ----------------------------------------------------
// DATE HELPER
// ----------------------------------------------------

private fun getStartOfTodayMillis(): Long {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

// ----------------------------------------------------
// MAIN APPLICATION
// ----------------------------------------------------

@Composable
fun BookPingApp() {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Database
    val database = remember {
        BookDatabase.getDatabase(context.applicationContext)
    }

    // Preferences
    val preferences = remember {
        UserPreferences(context.applicationContext)
    }

    // Repository
    val repository = remember {
        BookRepository(
            opacService = OpacService(),
            bookDao = database.bookDao()
        )
    }

    // Observe active books
    val activeBooks by repository.getActiveBooks()
        .collectAsState(initial = emptyList())

    // Observe saved preferences
    val savedMemberId by preferences.memberId
        .collectAsState(initial = "")

    val reminderDays by preferences.reminderDays
        .collectAsState(initial = 2)

    val repeatIntervalHours by preferences.repeatIntervalHours
        .collectAsState(initial = 24)

    // UI states
    var memberId by remember {
        mutableStateOf("")
    }

    var isSyncing by remember {
        mutableStateOf(false)
    }

    var syncMessage by remember {
        mutableStateOf("Ready to synchronize")
    }

    var lastSyncCount by remember {
        mutableIntStateOf(0)
    }

    // Restore saved member ID
    LaunchedEffect(savedMemberId) {
        if (savedMemberId.isNotBlank()) {
            memberId = savedMemberId
        }
    }

    // Dashboard calculations
    val todayStart = getStartOfTodayMillis()
    val dayMillis = 24L * 60L * 60L * 1000L

    val overdueCount = activeBooks.count {
        it.dueDateMillis < todayStart
    }

    val dueSoonCount = activeBooks.count {
        it.dueDateMillis >= todayStart &&
                it.dueDateMillis <= todayStart + (7 * dayMillis)
    }

    // ------------------------------------------------
    // MAIN UI
    // ------------------------------------------------

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Background
    ) {

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // HEADER
            item {
                Column {

                    Text(
                        text = "BookPing",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Dark
                    )

                    Text(
                        text = "Your library companion",
                        color = Muted,
                        fontSize = 14.sp
                    )
                }
            }

            // OPAC SYNCHRONIZATION
            item {

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        Text(
                            text = "KIOT Library",
                            fontWeight = FontWeight.SemiBold,
                            color = Dark
                        )

                        OutlinedTextField(
                            value = memberId,
                            onValueChange = {
                                memberId = it
                            },
                            label = {
                                Text("OPAC Member ID")
                            },
                            placeholder = {
                                Text("Enter your member ID")
                            },
                            singleLine = true,
                            enabled = !isSyncing,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {

                                if (memberId.isBlank() || isSyncing) {
                                    return@Button
                                }

                                scope.launch {

                                    isSyncing = true
                                    syncMessage =
                                        "Connecting to KIOT OPAC..."

                                    try {

                                        val cleanMemberId = memberId.trim()

                                        // Save member ID
                                        preferences.setMemberId(
                                            cleanMemberId
                                        )

                                        // Synchronize OPAC and Room
                                        val count = repository.syncBooks(
                                            context = context.applicationContext,
                                            memberId = cleanMemberId,
                                            reminderDays = reminderDays,
                                            repeatIntervalHours =
                                                repeatIntervalHours
                                        )

                                        lastSyncCount = count

                                        syncMessage =
                                            "Sync successful! $count book(s) found."

                                    } catch (e: Exception) {

                                        syncMessage =
                                            "Sync failed: ${
                                                e.message ?: "Unknown error"
                                            }"

                                    } finally {
                                        isSyncing = false
                                    }
                                }
                            },
                            enabled = memberId.isNotBlank() && !isSyncing,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Blue
                            )
                        ) {

                            if (isSyncing) {

                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )

                                Spacer(
                                    modifier = Modifier.width(8.dp)
                                )
                            }

                            Text(
                                text = if (isSyncing) {
                                    "Syncing..."
                                } else {
                                    "Sync now"
                                }
                            )
                        }

                        Text(
                            text = syncMessage,
                            fontSize = 12.sp,
                            color = when {
                                isSyncing -> Blue
                                syncMessage.startsWith("Sync failed") ->
                                    Color(0xFFD32F2F)
                                syncMessage.startsWith("Sync successful") ->
                                    Color(0xFF168447)
                                else -> Muted
                            }
                        )
                    }
                }
            }

            // LIBRARY OVERVIEW
            item {

                Column {

                    Text(
                        text = "Your library overview",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Dark
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        SummaryCard(
                            title = "Borrowed",
                            value = activeBooks.size.toString(),
                            modifier = Modifier.weight(1f)
                        )

                        SummaryCard(
                            title = "Due soon",
                            value = dueSoonCount.toString(),
                            modifier = Modifier.weight(1f)
                        )

                        SummaryCard(
                            title = "Overdue",
                            value = overdueCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // CURRENTLY BORROWED
            item {

                Text(
                    text = "Currently borrowed",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Dark
                )
            }

            // BOOK LIST
            if (activeBooks.isEmpty()) {

                item {

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        )
                    ) {

                        Text(
                            text = if (lastSyncCount > 0) {
                                "OPAC found $lastSyncCount book(s), but no active books are currently visible in Room. Check repository synchronization."
                            } else {
                                "No books found. Tap Sync now to check your library."
                            },
                            modifier = Modifier.padding(18.dp),
                            color = Muted,
                            fontSize = 13.sp
                        )
                    }
                }

            } else {

                items(
                    items = activeBooks,
                    key = { it.accessionNumber }
                ) { book ->

                    SyncedBookCard(book)
                }
            }

            // NOTIFICATION SETTINGS
            item {

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {

                    NotificationSettingsSection(
                        currentReminderDays = reminderDays,
                        currentRepeatHours = repeatIntervalHours,
                        onSave = { days, hours ->

                            scope.launch {

                                try {

                                    // Save preferences
                                    preferences.setReminderDays(days)

                                    preferences.setRepeatIntervalHours(
                                        hours
                                    )

                                    // Update schedules for all active books
                                    activeBooks.forEach { book ->

                                        ReminderScheduler.scheduleReminder(
                                            context = context.applicationContext,
                                            book = book,
                                            reminderDays = days,
                                            repeatIntervalHours = hours
                                        )
                                    }

                                    syncMessage =
                                        "Notification settings saved!"

                                } catch (e: Exception) {

                                    syncMessage =
                                        "Could not save settings: ${
                                            e.message ?: "Unknown error"
                                        }"
                                }
                            }
                        }
                    )
                }
            }

            // FOOTER
            item {

                Text(
                    text = "BookPing • KIOT Library Reminder",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    color = Muted,
                    fontSize = 12.sp
                )

            }
        }
    }
}

// ----------------------------------------------------
// NOTIFICATION SETTINGS
// ----------------------------------------------------

@Composable
fun NotificationSettingsSection(
    currentReminderDays: Int,
    currentRepeatHours: Int,
    onSave: (Int, Int) -> Unit
) {

    var reminderDays by remember(currentReminderDays) {
        mutableFloatStateOf(currentReminderDays.toFloat())
    }

    var repeatHours by remember(currentRepeatHours) {
        mutableFloatStateOf(currentRepeatHours.toFloat())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        Text(
            text = "Reminder settings",
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = Dark
        )

        Text(
            text = "Remind me before the due date",
            color = Muted,
            fontSize = 13.sp
        )

        Text(
            text = "${reminderDays.toInt()} days before",
            color = Blue,
            fontWeight = FontWeight.SemiBold
        )

        Slider(
            value = reminderDays,
            onValueChange = {
                reminderDays = it
            },
            valueRange = 1f..30f,
            steps = 28
        )

        HorizontalDivider(color = Color(0xFFEDF0F5))

        Text(
            text = "Repeat notification",
            color = Muted,
            fontSize = 13.sp
        )

        Text(
            text = "Every ${repeatHours.toInt()} hours",
            color = Blue,
            fontWeight = FontWeight.SemiBold
        )

        Slider(
            value = repeatHours,
            onValueChange = {
                repeatHours = it
            },
            valueRange = 1f..24f,
            steps = 22
        )

        Button(
            onClick = {
                onSave(
                    reminderDays.toInt(),
                    repeatHours.toInt()
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Notification Settings")
        }

        HorizontalDivider(color = Color(0xFFEDF0F5))

        Text(
            text = "Persistent notification",
            fontWeight = FontWeight.SemiBold,
            color = Dark
        )

        Text(
            text = "Notification remains ongoing until the book is returned.",
            fontSize = 12.sp,
            color = Muted
        )
    }
}

// ----------------------------------------------------
// DYNAMIC BOOK CARD
// ----------------------------------------------------

@Composable
fun SyncedBookCard(book: BookEntity) {

    val formatter = remember {
        SimpleDateFormat(
            "dd MMM yyyy",
            Locale.getDefault()
        )
    }

    val issueDate = formatter.format(
        Date(book.issueDateMillis)
    )

    val dueDate = formatter.format(
        Date(book.dueDateMillis)
    )

    val todayStart = getStartOfTodayMillis()
    val isOverdue = book.dueDateMillis < todayStart

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "KIOT LIBRARY",
                    fontSize = 11.sp,
                    color = Muted,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    color = if (isOverdue) {
                        Color(0xFFFFE8E8)
                    } else {
                        Color(0xFFE8F1FF)
                    },
                    shape = RoundedCornerShape(20.dp)
                ) {

                    Text(
                        text = if (isOverdue) {
                            "OVERDUE"
                        } else {
                            "BORROWED"
                        },
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 5.dp
                        ),
                        color = if (isOverdue) {
                            Color(0xFFD32F2F)
                        } else {
                            Blue
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = book.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Dark
            )

            Text(
                text = "Accession Number: ${book.accessionNumber}",
                color = Muted,
                fontSize = 13.sp
            )

            HorizontalDivider(
                color = Color(0xFFEDF0F5)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                DateInfo(
                    label = "ISSUE DATE",
                    date = issueDate
                )

                DateInfo(
                    label = "DUE DATE",
                    date = dueDate
                )
            }

            Surface(
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(12.dp)
            ) {

                Text(
                    text = "Return your book on or before $dueDate.",
                    modifier = Modifier.padding(12.dp),
                    color = Blue,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ----------------------------------------------------
// SUMMARY CARD
// ----------------------------------------------------

@Composable
fun SummaryCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
    ) {

        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            Text(
                text = value,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = Blue
            )

            Text(
                text = title,
                fontSize = 11.sp,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ----------------------------------------------------
// DATE INFORMATION
// ----------------------------------------------------

@Composable
fun DateInfo(
    label: String,
    date: String
) {

    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {

        Text(
            text = label,
            color = Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = date,
            color = Dark,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

// ----------------------------------------------------
// NOTIFICATION PERMISSION
// ----------------------------------------------------

@Composable
fun NotificationPermissionHandler() {

    val context = LocalContext.current

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->

            Toast.makeText(
                context,
                if (granted) {
                    "BookPing notifications enabled!"
                } else {
                    "Notification permission denied."
                },
                Toast.LENGTH_LONG
            ).show()
        }

    LaunchedEffect(Unit) {

        NotificationHelper.createNotificationChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            val permission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )

            if (permission != PackageManager.PERMISSION_GRANTED) {

                permissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }
}
