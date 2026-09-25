package uk.telegramgames.lodolock

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.app.admin.DevicePolicyManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val dataRepository = DataRepository.getInstance(application)
    private val usageStatsHelper = UsageStatsHelper(application)
    private val devicePolicyManager: android.app.admin.DevicePolicyManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            application.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
        } else null
    private val adminComponent: ComponentName? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
        ComponentName(application, KidLockDeviceAdminReceiver::class.java)
    } else null

    private val _dailyLimitMinutes = MutableLiveData<Int>()
    val dailyLimitMinutes: LiveData<Int> = _dailyLimitMinutes

    private val _remainingTimeMinutes = MutableLiveData<Int>()
    val remainingTimeMinutes: LiveData<Int> = _remainingTimeMinutes

    private val _codes = MutableLiveData<List<Code>>()
    val codes: LiveData<List<Code>> = _codes

    private val _isAccessibilityServiceEnabled = MutableLiveData<Boolean>()
    val isAccessibilityServiceEnabled: LiveData<Boolean> = _isAccessibilityServiceEnabled

    private val _isUsageStatsPermissionGranted = MutableLiveData<Boolean>()
    val isUsageStatsPermissionGranted: LiveData<Boolean> = _isUsageStatsPermissionGranted

    private val _isOverlayPermissionGranted = MutableLiveData<Boolean>()
    val isOverlayPermissionGranted: LiveData<Boolean> = _isOverlayPermissionGranted

    private val _isAutostartEnabled = MutableLiveData<Boolean>()
    val isAutostartEnabled: LiveData<Boolean> = _isAutostartEnabled

    private val _isBlockingEnabled = MutableLiveData<Boolean>()
    val isBlockingEnabled: LiveData<Boolean> = _isBlockingEnabled

    private val _canEnableBlocking = MutableLiveData<Boolean>()
    val canEnableBlocking: LiveData<Boolean> = _canEnableBlocking

    private val _isPaidVersion = MutableLiveData<Boolean>()
    val isPaidVersion: LiveData<Boolean> = _isPaidVersion

    private val _isUnlockedUntilTomorrow = MutableLiveData<Boolean>()
    val isUnlockedUntilTomorrow: LiveData<Boolean> = _isUnlockedUntilTomorrow

    private val _isScheduleEnabled = MutableLiveData<Boolean>()
    val isScheduleEnabled: LiveData<Boolean> = _isScheduleEnabled

    private val _isTimerEnabled = MutableLiveData<Boolean>()
    val isTimerEnabled: LiveData<Boolean> = _isTimerEnabled

    private val _timeWindows = MutableLiveData<List<TimeWindow>>()
    val timeWindows: LiveData<List<TimeWindow>> = _timeWindows

    private val _isDeviceAdminActive = MutableLiveData<Boolean>()
    val isDeviceAdminActive: LiveData<Boolean> = _isDeviceAdminActive

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    init {
        loadSettings()
        loadCodes()
        checkPermissions()
        updateRemainingTime()
        loadScheduleSettings()
    }

    fun loadSettings() {
        // Check daily reset first before loading settings
        dataRepository.ensureDailyResetIfNeeded()

        _dailyLimitMinutes.value = dataRepository.getDailyTimeLimitMinutes()
        _isAutostartEnabled.value = dataRepository.isAutostartEnabled()
        _isPaidVersion.value = dataRepository.isPaidVersion()
        _isUnlockedUntilTomorrow.value = dataRepository.isUnlockedUntilTomorrow()

        val blockingEnabled = dataRepository.isBlockingEnabled()
        _isBlockingEnabled.value = blockingEnabled
        _isTimerEnabled.value = dataRepository.isTimerEnabled()

        val isAccessibilityEnabled = ScreenTimeAccessibilityService.isServiceEnabled(
            getApplication()
        )
        val isUsageStatsGranted = usageStatsHelper.hasUsageStatsPermission()
        val isOverlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(getApplication())
        } else {
            true
        }

        val allPermissionsGranted = isAccessibilityEnabled && isUsageStatsGranted && isOverlayGranted
        _canEnableBlocking.value = allPermissionsGranted
    }

    fun setDailyLimitMinutes(minutes: Int) {
        if (minutes < 0) {
            _message.value = getApplication<Application>().getString(R.string.limit_negative_error)
            return
        }
        dataRepository.setDailyTimeLimitMinutes(minutes)
        _dailyLimitMinutes.value = minutes
        val formattedTime = TimeManager.formatMinutes(getApplication(), minutes)
        _message.value = getApplication<Application>().getString(R.string.limit_set_format, formattedTime)
        updateRemainingTime()
    }

    fun generateCodes(count: Int, minutesPerCode: Int = 30) {
        Log.d("KidLock", "AdminViewModel.generateCodes() called: count=$count, minutesPerCode=$minutesPerCode")
        if (count <= 0 || count > 100) {
            Log.w("KidLock", "AdminViewModel.generateCodes() - invalid count: $count")
            _message.value = getApplication<Application>().getString(R.string.code_count_error)
            return
        }
        if (minutesPerCode < 0) {
            Log.w("KidLock", "AdminViewModel.generateCodes() - negative minutesPerCode: $minutesPerCode")
            _message.value = getApplication<Application>().getString(R.string.time_negative_error)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            Log.d("KidLock", "AdminViewModel.generateCodes() - generating codes on IO dispatcher")
            val newCodes = dataRepository.generateCodes(minutesPerCode, count)
            Log.d("KidLock", "AdminViewModel.generateCodes() - generated ${newCodes.size} codes")
            withContext(Dispatchers.Main) {
                loadCodes()
                _message.value = getApplication<Application>().getString(
                    R.string.codes_generated_format,
                    newCodes.size,
                    minutesPerCode
                )
                Log.d("KidLock", "AdminViewModel.generateCodes() - finished, message set")
            }
        }
    }

    fun loadCodes() {
        Log.d("KidLock", "AdminViewModel.loadCodes() called")
        val allCodes = dataRepository.getCodes()
        Log.d("KidLock", "AdminViewModel.loadCodes() - loaded ${allCodes.size} codes")
        _codes.postValue(allCodes)
        Log.d("KidLock", "AdminViewModel.loadCodes() - LiveData updated via postValue")
    }

    fun deleteCode(code: Code) {
        val codes = _codes.value?.toMutableList() ?: return
        codes.removeAll { it.value == code.value }
        dataRepository.saveCodes(codes)
        loadCodes()
        _message.value = getApplication<Application>().getString(R.string.code_deleted)
    }

    fun changePin(newPin: String) {
        if (newPin.length != 6 || !newPin.all { it.isDigit() }) {
            _message.value = getApplication<Application>().getString(R.string.pin_format_error)
            return
        }
        dataRepository.setPin(newPin)
        _message.value = getApplication<Application>().getString(R.string.pin_changed)
    }

    fun toggleUnlockUntilTomorrow() {
        val isCurrentlyUnlocked = dataRepository.isUnlockedUntilTomorrow()

        if (isCurrentlyUnlocked) {
            // Lock until tomorrow: set large negative addedTime to zero out remaining time
            val dailyLimit = dataRepository.getDailyTimeLimitMinutes()
            dataRepository.setAddedTimeMinutes(-dailyLimit - 1440)
            dataRepository.setUnlockedUntilTomorrow(false)
            _isUnlockedUntilTomorrow.value = false
            _message.value = getApplication<Application>().getString(R.string.time_locked)
        } else {
            // Unlock until tomorrow: add time until midnight
            val minutesUntilMidnight = TimeManager.getMinutesUntilMidnight()
            dataRepository.setAddedTimeMinutes(minutesUntilMidnight)
            dataRepository.setUnlockedUntilTomorrow(true)
            _isUnlockedUntilTomorrow.value = true
            _message.value = getApplication<Application>().getString(R.string.time_unlocked)
        }
        updateRemainingTime()
    }

    fun updateRemainingTime() {
        viewModelScope.launch(Dispatchers.IO) {
            // Check if daily reset is needed (this also resets unlock state)
            val wasReset = dataRepository.ensureDailyResetIfNeeded()
            if (wasReset) {
                _isUnlockedUntilTomorrow.postValue(false)
            }

            val remaining = usageStatsHelper.getRemainingTimeMinutes(
                dataRepository.getDailyTimeLimitMinutes(),
                dataRepository.getAddedTimeMinutes()
            )

            _remainingTimeMinutes.postValue(remaining)
        }
    }

    fun checkPermissions() {
        val isAccessibilityEnabled = ScreenTimeAccessibilityService.isServiceEnabled(
            getApplication()
        )
        val isUsageStatsGranted = usageStatsHelper.hasUsageStatsPermission()
        val isOverlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(getApplication())
        } else {
            true
        }

        _isAccessibilityServiceEnabled.value = isAccessibilityEnabled
        _isUsageStatsPermissionGranted.value = isUsageStatsGranted
        _isOverlayPermissionGranted.value = isOverlayGranted

        val allPermissionsGranted = isAccessibilityEnabled && isUsageStatsGranted && isOverlayGranted
        _canEnableBlocking.value = allPermissionsGranted
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${getApplication<Application>().packageName}")
            )
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            getApplication<Application>().startActivity(intent)
        }
    }

    fun setAutostartEnabled(enabled: Boolean) {
        dataRepository.setAutostartEnabled(enabled)
        _isAutostartEnabled.value = enabled
        _message.value = if (enabled) {
            getApplication<Application>().getString(R.string.autostart_enabled)
        } else {
            getApplication<Application>().getString(R.string.autostart_disabled)
        }
    }

    fun setBlockingEnabled(enabled: Boolean) {
        // Проверяем разрешения перед включением
        val isAccessibilityEnabled = _isAccessibilityServiceEnabled.value ?: false
        val isUsageStatsGranted = _isUsageStatsPermissionGranted.value ?: false
        val isOverlayGranted = _isOverlayPermissionGranted.value ?: false
        val allPermissionsGranted = isAccessibilityEnabled && isUsageStatsGranted && isOverlayGranted

        // ON sans permissions → impossible
        if (enabled && !allPermissionsGranted) {
            _message.value = getApplication<Application>().getString(R.string.blocking_requires_permissions)
            return
        }

        // Logique centralisée dans DataRepository :
        // ON → schedule si des plages existent, sinon timer. OFF → les deux off.
        dataRepository.setBlockingEnabled(enabled)

        _isTimerEnabled.value = dataRepository.isTimerEnabled()
        _isScheduleEnabled.value = dataRepository.isScheduleEnabled()
        _isBlockingEnabled.value = dataRepository.isBlockingEnabled()
        _message.value = if (enabled) {
            getApplication<Application>().getString(R.string.blocking_enabled)
        } else {
            getApplication<Application>().getString(R.string.blocking_disabled)
        }
    }

    fun setPaidVersion(isPaid: Boolean) {
        dataRepository.setPaidVersion(isPaid)
        _isPaidVersion.value = isPaid
    }

    fun clearMessage() {
        _message.value = null
    }

    fun showMessage(message: String) {
        _message.value = message
    }

    fun loadScheduleSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScheduleEnabled.postValue(dataRepository.isScheduleEnabled())
            _timeWindows.postValue(dataRepository.getTimeWindows())
        }
    }

    fun setScheduleEnabled(enabled: Boolean) {
        // Un schedule sans plage ne bloque rien : on refuse l'activation à vide.
        if (enabled && dataRepository.getTimeWindows().isEmpty()) {
            _message.value = getApplication<Application>().getString(R.string.schedule_requires_window)
            return
        }
        dataRepository.setScheduleEnabled(enabled)
        _isScheduleEnabled.value = enabled
    }

    fun setTimerEnabled(enabled: Boolean) {
        dataRepository.setTimerEnabled(enabled)
        _isTimerEnabled.value = enabled
    }

    fun addTimeWindow(window: TimeWindow) {
        if (!dataRepository.canAddTimeWindow(window)) {
            _message.value = getApplication<Application>().getString(R.string.schedule_overlap_error)
            return
        }
        if (!dataRepository.addTimeWindow(window)) {
            _message.value = getApplication<Application>().getString(R.string.schedule_max_reached)
            return
        }
        _timeWindows.value = dataRepository.getTimeWindows()
        _message.value = getApplication<Application>().getString(R.string.schedule_window_added)
    }

    fun updateTimeWindow(window: TimeWindow) {
        if (!dataRepository.canAddTimeWindow(window, excludeId = window.id)) {
            _message.value = getApplication<Application>().getString(R.string.schedule_overlap_error)
            return
        }
        dataRepository.updateTimeWindow(window)
        _timeWindows.value = dataRepository.getTimeWindows()
        _message.value = getApplication<Application>().getString(R.string.schedule_window_updated)
    }

    fun removeTimeWindow(id: String) {
        dataRepository.removeTimeWindow(id)
        _timeWindows.value = dataRepository.getTimeWindows()
        _message.value = getApplication<Application>().getString(R.string.schedule_window_removed)
    }

    fun checkDeviceAdminStatus() {
        val isActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO && devicePolicyManager != null && adminComponent != null) {
            devicePolicyManager.isAdminActive(adminComponent)
        } else false
        _isDeviceAdminActive.postValue(isActive)
    }

    fun openDeviceAdminSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO && adminComponent != null) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getApplication<Application>().getString(R.string.btn_activate_device_admin)
                )
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(intent)
        }
    }
}
