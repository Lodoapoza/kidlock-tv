package uk.telegramgames.kidlock

import android.content.Context
import android.util.Log
import java.util.Calendar

object ScheduleManager {

    private const val TAG = "ScheduleManager"

    /**
     * Returns true if the given time (in minutes since midnight) falls inside
     * the window [start, end). Handles midnight-crossing windows (start >= end).
     */
    private fun isTimeInWindow(nowMinutes: Int, start: Int, end: Int): Boolean {
        return if (start == end) {
            false
        } else if (start < end) {
            nowMinutes >= start && nowMinutes < end
        } else {
            // Midnight-crossing: e.g. 22h00 → 07h00 → [1320, 420)
            nowMinutes >= start || nowMinutes < end
        }
    }

    /**
     * Fenêtre active sur le jour pertinent.
     * Pour une fenêtre qui traverse minuit (ex. 22h00 → 07h00), la partie
     * « après minuit » (00h → 07h) appartient au jour où la fenêtre a commencé
     * (la veille), pas au jour courant. Ex. : fenêtre active le lundi →
     * le lundi 22h-24h ET le mardi 00h-07h sont bloqués.
     */
    private fun isWindowActiveOnDay(window: TimeWindow, now: Calendar): Boolean {
        if (window.daysOfWeek.isEmpty()) return true

        val start = window.toStartMinutes()
        val end = window.toEndMinutes()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        if (start < end) {
            // Fenêtre normale : le jour courant suffit
            return window.isActiveOnDay(now)
        }

        // Midnight-crossing
        return if (nowMinutes >= start) {
            // 22h00 → minuit : jour courant
            window.isActiveOnDay(now)
        } else {
            // minuit → fin : jour de début (la veille)
            val yesterday = Calendar.getInstance().apply {
                timeInMillis = now.timeInMillis - 24 * 60 * 60 * 1000L
            }
            window.isActiveOnDay(yesterday)
        }
    }

    /** Returns true if schedule is enabled AND now is within an allowed window. */
    fun isWithinAllowedWindow(nowCalendar: Calendar, repo: DataRepository): Boolean {
        if (!repo.isScheduleEnabled()) return false

        val windows = repo.getTimeWindows()
        if (windows.isEmpty()) return false

        val nowMinutes = nowCalendar.get(Calendar.HOUR_OF_DAY) * 60 + nowCalendar.get(Calendar.MINUTE)

        val activeWindows = windows.filter { window ->
            val start = window.toStartMinutes()
            val end = window.toEndMinutes()
            isTimeInWindow(nowMinutes, start, end) && isWindowActiveOnDay(window, nowCalendar)
        }
        // ALLOW is an explicit exception and wins over a BLOCK if malformed
        // or legacy data contains overlapping windows.
        return activeWindows.any { it.type == TimeWindowType.ALLOW }
    }

    /**
     * Returns true when the global schedule is currently blocking.
     * A BLOCK window applies to all user applications; ALLOW windows remain
     * explicit exceptions for overlapping or legacy configurations.
     */
    fun shouldBlockNow(nowCalendar: Calendar, repo: DataRepository): Boolean {
        if (!repo.isScheduleEnabled()) return false
        if (ScheduleManager.isScheduleBypassed(repo)) return false

        val windows = repo.getTimeWindows()
        if (windows.isEmpty()) return false
        val nowMinutes = nowCalendar.get(Calendar.HOUR_OF_DAY) * 60 + nowCalendar.get(Calendar.MINUTE)
        val activeWindows = windows.filter { window ->
            val start = window.toStartMinutes()
            val end = window.toEndMinutes()
            isTimeInWindow(nowMinutes, start, end) && isWindowActiveOnDay(window, nowCalendar)
        }
        if (activeWindows.any { it.type == TimeWindowType.ALLOW }) return false
        return activeWindows.any { it.type == TimeWindowType.BLOCK }
    }

    /** Returns the active BLOCK time window for the given time, or null. */
    fun getActiveBlockWindow(nowCalendar: Calendar, repo: DataRepository): TimeWindow? {
        if (!repo.isScheduleEnabled()) return null
        if (ScheduleManager.isScheduleBypassed(repo)) return null

        val windows = repo.getTimeWindows()
        if (windows.isEmpty()) return null

        val nowMinutes = nowCalendar.get(Calendar.HOUR_OF_DAY) * 60 + nowCalendar.get(Calendar.MINUTE)

        for (window in windows) {
            val start = window.toStartMinutes()
            val end = window.toEndMinutes()

            if (isTimeInWindow(nowMinutes, start, end) &&
                window.type == TimeWindowType.BLOCK &&
                isWindowActiveOnDay(window, nowCalendar)) {
                return window
            }
        }

        return null
    }

    /**
     * Returns the next block END time that is > now, iterating days 0..7 from today.
     * Used to display « Disponible à HH:MM » on the schedule lock screen.
     * Gère le passage à minuit : si une fenêtre est active maintenant, on retourne
     * la fin DE CETTE fenêtre (ex. 22h→07h active, il est 01h → fin 07h aujourd'hui).
     */
    fun nextBlockedStartMillis(nowCalendar: Calendar, repo: DataRepository): Long {
        val nowMillis = nowCalendar.timeInMillis

        // Fenêtre active maintenant → retourner sa fin (gère le passage à minuit).
        getActiveBlockWindow(nowCalendar, repo)?.let { active ->
            return windowEndMillis(nowCalendar, active)
        }

        val windows = repo.getTimeWindows()
        if (windows.isEmpty()) {
            return nowMillis + 24 * 60 * 60 * 1000L
        }

        var nextEnd: Long? = null
        for (i in 0..7) {
            val checkCalendar = Calendar.getInstance()
            checkCalendar.timeInMillis = nowMillis
            checkCalendar.add(Calendar.DAY_OF_YEAR, i)
            val dayStart = startOfDayMillis(checkCalendar)

            for (window in windows) {
                if (window.type != TimeWindowType.BLOCK) continue
                if (!window.isActiveOnDay(checkCalendar)) continue

                val start = window.toStartMinutes()
                val end = window.toEndMinutes()

                val endMillis = if (start < end) {
                    dayStart + end * 60 * 1000L
                } else {
                    // Midnight-crossing: end is next day
                    dayStart + 24 * 60 * 60 * 1000L + end * 60 * 1000L
                }
                if (endMillis > nowMillis && (nextEnd == null || endMillis < nextEnd)) {
                    nextEnd = endMillis
                }
            }
        }

        return nextEnd ?: (nowMillis + 24 * 60 * 60 * 1000L)
    }

    /**
     * Retourne le début (en millis) de la PROCHAINE fenêtre BLOCK à venir
     * (strictement après maintenant), ou null s'il n'y en a aucune.
     * Utilisé pour afficher « Prochaine plage de blocage : HH:MM » sur l'écran idle.
     */
    fun getNextBlockWindowStart(nowCalendar: Calendar, repo: DataRepository): Long? {
        val nowMillis = nowCalendar.timeInMillis
        val windows = repo.getTimeWindows()
        if (windows.isEmpty()) return null

        for (i in 0..7) {
            val check = Calendar.getInstance()
            check.timeInMillis = nowMillis
            check.add(Calendar.DAY_OF_YEAR, i)
            val dayStart = startOfDayMillis(check)
            for (window in windows) {
                if (window.type != TimeWindowType.BLOCK) continue
                if (!isWindowActiveOnDay(window, check)) continue
                val startMillis = dayStart + window.toStartMinutes() * 60_000L
                if (startMillis > nowMillis) return startMillis
            }
        }
        return null
    }

    /**
     * Fin (en millis) de la fenêtre donnée, évaluée à partir de maintenant.
     * Tient compte du passage à minuit : si on est dans la partie « après minuit »
     * (ex. 01:00 pour une fenêtre 22h→07h démarrée la veille), la fin est
     * aujourd'hui à endMinutes ; sinon (partie « avant minuit », ex. 23:00)
     * la fin est demain à endMinutes.
     */
    fun windowEndMillis(now: Calendar, window: TimeWindow): Long {
        val dayStart = startOfDayMillis(now)
        val start = window.toStartMinutes()
        val end = window.toEndMinutes()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return if (start < end) {
            dayStart + end * 60_000L
        } else if (nowMinutes >= start) {
            dayStart + 24 * 60 * 60 * 1000L + end * 60_000L
        } else {
            dayStart + end * 60_000L
        }
    }

    /** Returns true if the schedule unlock timer has not yet expired. */
    fun isScheduleBypassed(repo: DataRepository): Boolean {
        val unlockUntil = repo.getScheduleUnlockUntil()
        if (unlockUntil == 0L) return false

        val now = System.currentTimeMillis()
        return unlockUntil > now
    }

    /** Clears the schedule unlock timer (sets it to 0). */
    fun clearScheduleUnlock(repo: DataRepository) {
        repo.setScheduleUnlockUntil(0L)
    }

    /** Sets the schedule unlock timer to the given millis. */
    fun setScheduleUnlockUntil(repo: DataRepository, millis: Long) {
        repo.setScheduleUnlockUntil(millis)
    }

    /** Returns the start of day in milliseconds for the given Calendar. */
    private fun startOfDayMillis(calendar: Calendar): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = calendar.timeInMillis
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /**
     * Détermine la VRAIE raison de blocage à l'instant T.
     * Retourne "SCHEDULE" (une fenêtre BLOCK est active maintenant),
     * "TIMER" (le quota quotidien est épuisé), sinon null (aucun blocage effectif).
     */
    fun getActiveBlockReason(context: Context, repo: DataRepository): String? {
        if (!repo.isBlockingEnabled()) return null

        val now = Calendar.getInstance()

        // 1. Fenêtre de blocage active ?
        if (getActiveBlockWindow(now, repo) != null) {
            return "SCHEDULE"
        }

        // 2. Quota quotidien épuisé ?
        if (repo.isTimerEnabled()) {
            repo.ensureDailyResetIfNeeded()
            val limit = repo.getDailyTimeLimitMinutes()
            val added = repo.getAddedTimeMinutes()
            if (!UsageStatsHelper(context).hasRemainingTime(limit, added)) {
                return "TIMER"
            }
        }

        return null
    }
}
