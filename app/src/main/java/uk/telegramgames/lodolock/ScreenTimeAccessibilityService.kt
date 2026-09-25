package uk.telegramgames.lodolock

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import java.util.Calendar

class ScreenTimeAccessibilityService : AccessibilityService() {

    private lateinit var dataRepository: DataRepository
    private lateinit var usageStatsHelper: UsageStatsHelper
    private val handler = Handler(Looper.getMainLooper())
    private var periodicCheckRunnable: Runnable? = null
    // Short interval so we react quickly to overuse on TV devices
    private val CHECK_INTERVAL_MS = 1_000L
    @Volatile
    private var lastKnownPackageName: String? = null
    @Volatile
    private var lastBlockTime: Long = 0
    // Anti-boucle : ne pas rouvrir MainActivity toutes les 5 s si le service est désactivé
    @Volatile
    private var lastServiceDisabledOpen: Long = 0
    @Volatile
    private var blockNotificationShown = false
    @Volatile
    private var serviceDestroyed = false
    // Prevent rapid re-blocking loops when apps relaunch quickly
    private val MIN_BLOCK_INTERVAL_MS = 500L
    private val activityManager: ActivityManager by lazy {
        getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
    private val usageStatsManager: UsageStatsManager? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        } else {
            null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        dataRepository = DataRepository.getInstance(this)
        usageStatsHelper = UsageStatsHelper(this)
        createNotificationChannel()
        clearBlockNotification()

        Log.d(TAG, "onServiceConnected: schedule=${dataRepository.isScheduleEnabled()}, timer=${dataRepository.isTimerEnabled()}, blocking=${dataRepository.isBlockingEnabled()}")

        getCurrentPackageName()?.let {
            if (!isSystemPackage(it) && it != packageName) {
                lastKnownPackageName = it
            }
        }

        startPeriodicCheck()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Минимизация шума: реагируем только на смену окон
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            handleWindowStateChanged(event)
        }
    }

    /** Returns true when the global schedule is actively blocking all user apps. */
    private fun shouldBlockBySchedule(): Boolean {
        if (!dataRepository.isScheduleEnabled()) return false
        if (ScheduleManager.isScheduleBypassed(dataRepository)) return false
        return ScheduleManager.shouldBlockNow(Calendar.getInstance(), dataRepository)
    }

    /**
     * Returns the active BLOCK window for the current time, or null.
     */
    private fun getActiveBlockWindow(): TimeWindow? {
        if (!dataRepository.isScheduleEnabled()) return null
        if (ScheduleManager.isScheduleBypassed(dataRepository)) return null
        
        val now = Calendar.getInstance()
        return ScheduleManager.getActiveBlockWindow(now, dataRepository)
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        if (packageName == this.packageName) {
            lastKnownPackageName = packageName
            return
        }

        lastKnownPackageName = packageName

        if (!dataRepository.isBlockingEnabled()) {
            return
        }

        // Guard: bloquer Settings/Play Store quand le blocage est actif
        if (isGuardPackage(packageName)) {
            Log.d(TAG, "GUARD blocked: $packageName")
            blockAppLaunch(packageName)
            return
        }

        Log.d(TAG, "handleWindowStateChanged: pkg=$packageName, schedule=${dataRepository.isScheduleEnabled()}, timer=${dataRepository.isTimerEnabled()}")

        if (shouldBlockBySchedule()) {
            Log.d(TAG, "BLOCKED by schedule: $packageName")
            blockAppLaunch(packageName)
            return
        }

        if (!dataRepository.isTimerEnabled()) {
            return
        }

        dataRepository.ensureDailyResetIfNeeded()
        val dailyLimit = dataRepository.getDailyTimeLimitMinutes()
        val addedTime = dataRepository.getAddedTimeMinutes()
        val hasTime = usageStatsHelper.hasRemainingTime(dailyLimit, addedTime)

        if (!hasTime) {
            Log.d(TAG, "BLOCKED by timer: $packageName")
            // blockAppLaunch ferme l'app ET ouvre KidLock :
            // pas de forceCloseApp supplémentaire ici (son HOME renverrait
            // KidLock en arrière-plan et l'utilisateur resterait sur le launcher).
            blockAppLaunch(packageName)
        }
    }

    private fun isSystemPackage(packageName: String): Boolean {
        if (isGuardPackage(packageName)) return false
        return packageName.startsWith("com.android") ||
                packageName.startsWith("android") ||
                packageName == "com.google.android.leanbacklauncher"
    }

    /**
     * Returns true if this package should be blocked when blocking is active.
     * Blocks Settings, Play Store, and similar system apps that could be used
     * to disable KidLock permissions.
     */
    private fun isGuardPackage(packageName: String): Boolean {
        return packageName == "com.android.settings" ||
                packageName == "com.google.android.tv.settings" ||
                packageName == "com.android.vending" ||
                packageName == "com.google.android.gms"
    }

    private fun blockAppLaunch(packageName: String? = null, forcedReason: String? = null) {
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < MIN_BLOCK_INTERVAL_MS) {
            return
        }
        lastBlockTime = now

        val currentPackage = getCurrentPackageName()
        if (currentPackage == this.packageName) {
            return
        }

        lastKnownPackageName = null

        val reason = forcedReason ?: if (packageName != null) {
            if (shouldBlockBySchedule()) "SCHEDULE" else "TIMER"
        } else {
            if (shouldBlockBySchedule()) "SCHEDULE" else "TIMER"
        }

        // Fermer immédiatement l'app bloquée
        if (packageName != null) {
            forceCloseApp(packageName)
        }

        // Ouvrir KidLock
        val lodoLockIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("BLOCK_REASON", reason)
        }
        startActivity(lodoLockIntent)

        // Some TV launchers briefly regain focus after GLOBAL_ACTION_HOME or
        // after a killed app. Reassert KidLock once, while the schedule is
        // still active, so the child always returns to the lock screen.
        handler.postDelayed({
            if (dataRepository.isBlockingEnabled() &&
                (reason != "SCHEDULE" || shouldBlockBySchedule()) &&
                getCurrentPackageName() != this.packageName
            ) {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("BLOCK_REASON", reason)
                })
            }
        }, 250L)

        // Une seule notification par épisode de blocage, jamais pour un guard.
        if (reason != "GUARD" && reason != "SERVICE_DISABLED") {
            showBlockNotification(reason == "SCHEDULE")
        }
    }

    private fun showBlockNotification(isScheduleBlock: Boolean) {
        if (blockNotificationShown) return
        blockNotificationShown = true
        val channelId = "screen_time_block"
        val title = if (isScheduleBlock) {
            getString(R.string.schedule_blocked_title)
        } else {
            getString(R.string.time_limit_reached)
        }
        val text = if (isScheduleBlock) {
            getString(R.string.schedule_blocked_message)
        } else {
            getString(R.string.time_limit_reached_message)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(BLOCK_NOTIFICATION_ID, notification)
    }

    private fun clearBlockNotification() {
        clearBlockNotification(this)
        blockNotificationShown = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "screen_time_block",
                getString(R.string.time_limit_notifications),
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        serviceDestroyed = true
        super.onDestroy()
        stopPeriodicCheck()
    }

    private fun startPeriodicCheck() {
        stopPeriodicCheck()
        serviceDestroyed = false
        periodicCheckRunnable = object : Runnable {
            override fun run() {
                if (serviceDestroyed) return
                checkAndMinimizeApps()
                if (!serviceDestroyed) {
                    handler.postDelayed(this, CHECK_INTERVAL_MS)
                }
            }
        }
        handler.post(periodicCheckRunnable!!)
    }

    private fun stopPeriodicCheck() {
        periodicCheckRunnable?.let {
            handler.removeCallbacks(it)
            periodicCheckRunnable = null
        }
    }

    private fun checkAndMinimizeApps() {
        // Détecter si le service a été désactivé
        if (!isServiceEnabled(this)) {
            Log.w(TAG, "Accessibility service disabled! Attempting to re-enable...")
            // Anti-boucle : prévenir l'utilisateur au maximum 1×/30 s
            val now = System.currentTimeMillis()
            if (now - lastServiceDisabledOpen > 30_000L) {
                lastServiceDisabledOpen = now
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("BLOCK_REASON", "SERVICE_DISABLED")
                }
                startActivity(intent)
            }
            return
        }

        if (!dataRepository.isBlockingEnabled()) {
            clearBlockNotification()
            return
        }

        val currentPackageName = getCurrentPackageName()
        if (currentPackageName == null) {
            return
        }

        if (currentPackageName == this.packageName) {
            return
        }

        // Guard: bloquer Settings/Play Store en périodique aussi
        if (isGuardPackage(currentPackageName)) {
            Log.d(TAG, "PERIODIC GUARD blocked: $currentPackageName")
            blockAppLaunch(currentPackageName, "GUARD")
            return
        }

        if (shouldBlockBySchedule()) {
            Log.d(TAG, "PERIODIC BLOCKED by schedule: $currentPackageName")
            blockAppLaunch(currentPackageName)
            return
        }

        if (!dataRepository.isTimerEnabled()) {
            clearBlockNotification()
            return
        }

        if (currentPackageName != lastKnownPackageName) {
            lastKnownPackageName = currentPackageName
        }

        dataRepository.ensureDailyResetIfNeeded()
        val dailyLimit = dataRepository.getDailyTimeLimitMinutes()
        val addedTime = dataRepository.getAddedTimeMinutes()
        val hasTime = usageStatsHelper.hasRemainingTime(dailyLimit, addedTime)

        if (hasTime) {
            clearBlockNotification()
            return
        }

        Log.d(TAG, "PERIODIC BLOCKED by timer: $currentPackageName")
        blockAppLaunch(currentPackageName)
    }

    private fun getCurrentPackageName(): String? {
        return try {
            // The accessibility root is the freshest signal. Do not discard
            // launcher/system packages and fall back to an old app: doing so
            // could make the service believe the blocked app is still open.
            val fromAccessibility = rootInActiveWindow?.packageName?.toString()
            if (!fromAccessibility.isNullOrEmpty()) {
                return fromAccessibility
            }

            val fromUsageStats = getCurrentPackageFromUsageStats()
            if (!fromUsageStats.isNullOrEmpty()) {
                return fromUsageStats
            }

            val fromActivityManager = getCurrentPackageFromActivityManager()
            if (!fromActivityManager.isNullOrEmpty()) {
                return fromActivityManager
            }

            lastKnownPackageName
        } catch (e: Exception) {
            lastKnownPackageName
        }
    }

    private fun getCurrentPackageFromUsageStats(): String? {
        val manager = usageStatsManager ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null
        }

        return try {
            val time = System.currentTimeMillis()
            val stats = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                time - 10_000L, // Élargi à 10 secondes pour Android TV
                time
            ) ?: return null

            // Находим приложение с наибольшим временем в foreground
            var mostRecent: UsageStats? = null
            for (usageStats in stats) {
                if (mostRecent == null || usageStats.lastTimeUsed > mostRecent.lastTimeUsed) {
                    mostRecent = usageStats
                }
            }

            mostRecent?.packageName
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Получает текущее приложение через ActivityManager
     * Примечание: getRunningTasks требует специального разрешения и может не работать на новых версиях Android
     */
    private fun getCurrentPackageFromActivityManager(): String? {
        return try {
            // На Android 5.0+ getRunningTasks требует разрешения и может не работать
            // Используем только для старых версий Android
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                @Suppress("DEPRECATION")
                val runningTasks = activityManager.getRunningTasks(1)
                if (runningTasks.isNotEmpty()) {
                    return runningTasks[0].topActivity?.packageName
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Force ferme une application
     * 1. performGlobalAction(GLOBAL_ACTION_HOME) — envoie l'app à l'arrière-plan
     * 2. killBackgroundProcesses — tue le processus en arrière-plan
     * 3. Retry après un court délai si l'app est encore en foreground
     */
    private fun forceCloseApp(packageName: String) {
        try {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (e: Exception) {
            Log.w(TAG, "GLOBAL_ACTION_HOME failed", e)
        }
        try {
            activityManager.killBackgroundProcesses(packageName)
        } catch (e: Exception) {
            Log.w(TAG, "killBackgroundProcesses failed for $packageName", e)
        }
        // Retry: si l'app résiste, retuer après 500ms
        handler.postDelayed({
            try {
                val current = getCurrentPackageName()
                if (current == packageName) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    activityManager.killBackgroundProcesses(packageName)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Retry forceClose failed for $packageName", e)
            }
        }, 500)
    }

    companion object {
        private const val TAG = "KidLock-Accessibility"
        private const val BLOCK_NOTIFICATION_ID = 1

        fun clearBlockNotification(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(BLOCK_NOTIFICATION_ID)
        }

        fun isServiceEnabled(context: android.content.Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val serviceName = "${context.packageName}/${ScreenTimeAccessibilityService::class.java.name}"
            return enabledServices.contains(serviceName)
        }
    }
}
