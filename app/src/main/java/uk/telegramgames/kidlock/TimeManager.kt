package uk.telegramgames.kidlock

import android.content.Context
import java.util.Calendar

object TimeManager {
    private const val MILLIS_PER_MINUTE = 60 * 1000L

    fun shouldResetDailyLimit(lastResetDate: Long): Boolean {
        val calendar = Calendar.getInstance()
        val today = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return lastResetDate < today
    }

    fun getTodayStartTime(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun getMinutesUntilMidnight(): Int {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val midnight = calendar.timeInMillis
        
        return ((midnight - now) / MILLIS_PER_MINUTE).toInt()
    }

    fun minutesToMillis(minutes: Int): Long {
        return minutes * MILLIS_PER_MINUTE
    }

    fun millisToMinutes(millis: Long): Int {
        return (millis / MILLIS_PER_MINUTE).toInt()
    }

    fun formatMinutes(context: Context, minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> context.getString(R.string.time_format_hours_minutes, hours, mins)
            hours > 0 -> context.getString(R.string.time_format_hours_only, hours)
            else -> context.getString(R.string.time_format_minutes_only, mins)
        }
    }

    /**
     * Formate une date absolue (millis) en heure locale "HH:MM".
     * À utiliser pour un horaire précis ("Disponible à 07:00"), pas une durée.
     */
    fun formatTimeOfDay(millis: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = millis
        val h = calendar.get(Calendar.HOUR_OF_DAY)
        val m = calendar.get(Calendar.MINUTE)
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", h, m)
    }
}
