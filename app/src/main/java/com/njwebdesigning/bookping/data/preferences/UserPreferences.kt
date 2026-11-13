
package com.njwebdesigning.bookping.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.bookPingDataStore by preferencesDataStore(
    name = "bookping_preferences"
)

class UserPreferences(private val context: Context) {

    companion object {
        private val REMINDER_DAYS = intPreferencesKey("reminder_days")
        private val REPEAT_INTERVAL_HOURS =
            intPreferencesKey("repeat_interval_hours")
        private val MEMBER_ID = stringPreferencesKey("member_id")
        private val AUTO_SYNC = booleanPreferencesKey("auto_sync")
    }

    val reminderDays: Flow<Int> =
        context.bookPingDataStore.data.map { it[REMINDER_DAYS] ?: 2 }

    val repeatIntervalHours: Flow<Int> =
        context.bookPingDataStore.data.map {
            it[REPEAT_INTERVAL_HOURS] ?: 24
        }

    val memberId: Flow<String> =
        context.bookPingDataStore.data.map { it[MEMBER_ID] ?: "" }

    val autoSync: Flow<Boolean> =
        context.bookPingDataStore.data.map { it[AUTO_SYNC] ?: true }

    suspend fun setReminderDays(days: Int) {
        context.bookPingDataStore.edit {
            it[REMINDER_DAYS] = days.coerceIn(1, 30)
        }
    }

    suspend fun setRepeatIntervalHours(hours: Int) {
        context.bookPingDataStore.edit {
            it[REPEAT_INTERVAL_HOURS] = hours.coerceIn(1, 24)
        }
    }

    suspend fun setMemberId(memberId: String) {
        context.bookPingDataStore.edit {
            it[MEMBER_ID] = memberId
        }
    }

    suspend fun setAutoSync(enabled: Boolean) {
        context.bookPingDataStore.edit {
            it[AUTO_SYNC] = enabled
        }
    }
}
