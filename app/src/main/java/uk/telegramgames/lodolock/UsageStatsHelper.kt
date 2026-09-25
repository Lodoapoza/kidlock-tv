package uk.telegramgames.lodolock

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.annotation.RequiresApi

class UsageStatsHelper(private val context: Context) {
    private val usageStatsManager: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    fun hasUsageStatsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            val mode = appOps?.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } else {
            true
        }
    }

    fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun getTodayUsageTimeMillis(): Long {
        if (usageStatsManager == null) return 0L

        val todayStart = TimeManager.getTodayStartTime()
        val now = System.currentTimeMillis()

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            todayStart,
            now
        ) ?: return 0L

        val defaultLauncher = getDefaultLauncherPackage()
        var totalTime = 0L

        for (usageStats in usageStatsList) {
            if (!isIgnoredPackage(usageStats.packageName, defaultLauncher)) {
                totalTime += usageStats.totalTimeInForeground
            }
        }

        return totalTime
    }

    fun getRemainingTimeMinutes(
        dailyLimitMinutes: Int,
        addedTimeMinutes: Int
    ): Int {
        return getRemainingTimeBreakdown(dailyLimitMinutes, addedTimeMinutes).totalMinutes
    }

    fun hasRemainingTime(
        dailyLimitMinutes: Int,
        addedTimeMinutes: Int
    ): Boolean {
        return getRemainingTimeBreakdown(dailyLimitMinutes, addedTimeMinutes).totalMinutes > 0
    }

    fun getRemainingTimeBreakdown(
        dailyLimitMinutes: Int,
        addedTimeMinutes: Int
    ): RemainingTimeBreakdown {
        if (!hasUsageStatsPermission()) {
            return RemainingTimeBreakdown(
                totalMinutes = 0,
                dailyRemaining = 0,
                bonusRemaining = 0
            )
        }

        val usedTimeMinutes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            TimeManager.millisToMinutes(getTodayUsageTimeMillis())
        } else {
            0
        }

        val dailyRemaining = (dailyLimitMinutes - usedTimeMinutes).coerceAtLeast(0)
        val extraUsed = (usedTimeMinutes - dailyLimitMinutes).coerceAtLeast(0)
        val bonusRemaining = (addedTimeMinutes - extraUsed).coerceAtLeast(0)

        return RemainingTimeBreakdown(
            totalMinutes = dailyRemaining + bonusRemaining,
            dailyRemaining = dailyRemaining,
            bonusRemaining = bonusRemaining
        )
    }

    data class RemainingTimeBreakdown(
        val totalMinutes: Int = 0,
        val dailyRemaining: Int = 0,
        val bonusRemaining: Int = 0
    )

    private fun isIgnoredPackage(packageName: String, defaultLauncher: String?): Boolean {
        // 1. KidLock itself
        if (packageName == context.packageName) return true

        // 2. Default Launcher
        if (packageName == defaultLauncher) return true

        // 3. Known TV Launchers and System UI
        return packageName == "com.google.android.leanbacklauncher" ||
                packageName == "com.google.android.tvlauncher" ||
                packageName == "com.android.systemui" ||
                packageName == "android" ||
                packageName == "com.google.android.tv.settings" ||
                packageName == "com.android.settings" ||
                packageName == "com.google.android.backdrop" // Screensaver
    }

    private fun getDefaultLauncherPackage(): String? {
        return try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = context.packageManager.resolveActivity(intent, 0)
            resolveInfo?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }
    }
}
