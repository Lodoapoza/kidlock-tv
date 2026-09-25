package uk.telegramgames.kidlock

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val dataRepository = DataRepository.getInstance(application)
    private val usageStatsHelper = UsageStatsHelper(application)

    private val _isAccessibilityServiceEnabled = MutableLiveData<Boolean>()
    val isAccessibilityServiceEnabled: LiveData<Boolean> = _isAccessibilityServiceEnabled

    private val _isUsageStatsPermissionGranted = MutableLiveData<Boolean>()
    val isUsageStatsPermissionGranted: LiveData<Boolean> = _isUsageStatsPermissionGranted

    private val _isOverlayPermissionGranted = MutableLiveData<Boolean>()
    val isOverlayPermissionGranted: LiveData<Boolean> = _isOverlayPermissionGranted

    private val _selectedDailyLimitMinutes = MutableLiveData<Int?>()
    val selectedDailyLimitMinutes: LiveData<Int?> = _selectedDailyLimitMinutes

    private val _generatedCodes = MutableLiveData<List<Code>>()
    val generatedCodes: LiveData<List<Code>> = _generatedCodes

    private val _pinErrorMessage = MutableLiveData<String?>()
    val pinErrorMessage: LiveData<String?> = _pinErrorMessage

    private val _blockingMode = MutableLiveData<String?>()
    val blockingMode: LiveData<String?> = _blockingMode

    init {
        _selectedDailyLimitMinutes.value = null
        _blockingMode.value = null
        refreshPermissions()
    }

    fun refreshPermissions() {
        _isAccessibilityServiceEnabled.value = ScreenTimeAccessibilityService.isServiceEnabled(getApplication())
        _isUsageStatsPermissionGranted.value = usageStatsHelper.hasUsageStatsPermission()
        _isOverlayPermissionGranted.value = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(getApplication())
        } else true
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        getApplication<Application>().startActivity(intent)
    }

    fun openUsageStatsSettings() {
        usageStatsHelper.requestUsageStatsPermission()
    }

    fun openOverlaySettings() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${getApplication<Application>().packageName}")
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            getApplication<Application>().startActivity(intent)
        }
    }

    fun setDailyLimitMinutes(minutes: Int) {
        if (minutes <= 0) {
            return
        }
        dataRepository.setDailyTimeLimitMinutes(minutes)
        _selectedDailyLimitMinutes.value = minutes
    }

    fun setAdminPin(pin: String): Boolean {
        if (pin.length != 6 || !pin.all { it.isDigit() }) {
            _pinErrorMessage.value = getApplication<Application>().getString(R.string.pin_format_error)
            return false
        }
        dataRepository.setPin(pin)
        dataRepository.setOnboardingCompleted()
        maybeEnableBlockingAndAutostart()
        _pinErrorMessage.value = null
        return true
    }

    fun generateInitialCodes(): List<Code> {
        val codes = dataRepository.generateCodesWithMinutes(listOf(30, 60))
        _generatedCodes.value = codes
        return codes
    }

    fun clearPinError() {
        _pinErrorMessage.value = null
    }

    fun setBlockingMode(mode: String) {
        _blockingMode.value = mode
        if (mode == "timer") {
            dataRepository.setTimerEnabled(true)
            dataRepository.setScheduleEnabled(false)
        } else if (mode == "schedule") {
            dataRepository.setTimerEnabled(false)
            // Ne pas activer schedule_enabled ici : un schedule sans plage ne bloque rien.
            // L'activation se fait automatiquement à l'ajout d'une plage (addTimeWindow).
        }
    }

    fun setAutostartEnabled(enabled: Boolean) {
        dataRepository.setAutostartEnabled(enabled)
    }

    private fun maybeEnableBlockingAndAutostart() {
        refreshPermissions()
        val accessibilityEnabled = _isAccessibilityServiceEnabled.value == true
        val usageStatsGranted = _isUsageStatsPermissionGranted.value == true
        val overlayGranted = _isOverlayPermissionGranted.value == true
        if (accessibilityEnabled && usageStatsGranted && overlayGranted) {
            dataRepository.setBlockingEnabled(true)
            dataRepository.setAutostartEnabled(true)
        }
    }
}
