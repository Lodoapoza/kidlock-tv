package uk.telegramgames.kidlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("KidLock", "BootReceiver - received ${intent.action}")
            val dataRepository = DataRepository.getInstance(context)

            dataRepository.ensureDailyResetIfNeeded()

            if (dataRepository.isAutostartEnabled() && dataRepository.isBlockingEnabled()) {
                Log.d("KidLock", "BootReceiver - autostart and blocking enabled, starting MainActivity")
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(launchIntent)
            } else {
                Log.d("KidLock", "BootReceiver - autostart or blocking disabled, not starting")
            }
        }
    }
}
