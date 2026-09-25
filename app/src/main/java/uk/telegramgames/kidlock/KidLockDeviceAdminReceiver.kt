package uk.telegramgames.kidlock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Device Admin receiver for anti-uninstall protection.
 * When active, Android blocks uninstallation of the app.
 * Deactivation can only be done from the KidLock admin panel (PIN-protected).
 */
class KidLockDeviceAdminReceiver : DeviceAdminReceiver() {
    
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Protection activée", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Protection désactivée", Toast.LENGTH_SHORT).show()
    }
    
    override fun onPasswordChanged(context: Context, intent: Intent) {
        // Not used
    }
    
    override fun onPasswordFailed(context: Context, intent: Intent) {
        // Not used
    }
    
    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        // Not used
    }
}