package uk.telegramgames.kidlock

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class CodeInputViewModel(application: Application) : AndroidViewModel(application) {
    private val dataRepository = DataRepository.getInstance(application)
    private val usageStatsHelper = UsageStatsHelper(application)

    private val _remainingTimeMinutes = MutableLiveData<Int>()
    val remainingTimeMinutes: LiveData<Int> = _remainingTimeMinutes

    private val _codeInput = MutableLiveData<String>()
    val codeInput: LiveData<String> = _codeInput

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _isCodeValid = MutableLiveData<Boolean>()
    val isCodeValid: LiveData<Boolean> = _isCodeValid

    private val _shouldOpenAdmin = MutableLiveData<Boolean>()
    val shouldOpenAdmin: LiveData<Boolean> = _shouldOpenAdmin

    private val _scheduleAdminChoice = MutableLiveData<Long?>()
    val scheduleAdminChoice: LiveData<Long?> = _scheduleAdminChoice

    init {
        updateRemainingTime()
    }

    fun setCodeInput(input: String) {
        _codeInput.value = input
        _message.value = null
    }

    fun addCharToCode(char: Char) {
        val current = _codeInput.value ?: ""
        if (current.length < 6) {
            val newCode = current + char
            _codeInput.value = newCode

            // Automatically validate as soon as the 6th character is entered
            if (newCode.length == 6) {
                activateCode()
            }
        }
    }

    fun removeCharFromCode() {
        val current = _codeInput.value ?: ""
        if (current.isNotEmpty()) {
            _codeInput.value = current.dropLast(1)
        }
    }

    fun clearCode() {
        _codeInput.value = ""
        _message.value = null
    }

    fun activateCode() {
        // Rate limiting
        if (dataRepository.isCodeLockedOut()) {
            val remaining = (dataRepository.getCodeLockoutUntil() - System.currentTimeMillis()) / 1000
            _message.value = getApplication<Application>().getString(R.string.rate_limit_lockout, remaining.toInt())
            _isCodeValid.value = false
            return
        }

        val codeValue = _codeInput.value ?: ""
        if (codeValue.length != 6) {
            _message.value = getApplication<Application>().getString(R.string.code_length_error)
            _isCodeValid.value = false
            return
        }

        dataRepository.ensureDailyResetIfNeeded()

        val code = dataRepository.findCode(codeValue)
        if (code != null) {
            if (code.isUsed) {
                _message.value = getApplication<Application>().getString(R.string.code_already_used_error)
                _isCodeValid.value = false
                return
            }

            val updatedCode = code.copy(
                isUsed = true,
                usedDate = Date()
            )
            dataRepository.updateCode(updatedCode)

            // A valid child access code must actually release the current lock.
            // In schedule mode, adding minutes alone has no effect because the
            // schedule engine does not use the daily timer. Create a persisted
            // bypass until the end of the active BLOCK window instead.
            val activeScheduleWindow = if (dataRepository.isScheduleEnabled()) {
                ScheduleManager.getActiveBlockWindow(Calendar.getInstance(), dataRepository)
            } else {
                null
            }
            if (activeScheduleWindow != null) {
                dataRepository.setScheduleUnlockUntil(
                    ScheduleManager.windowEndMillis(Calendar.getInstance(), activeScheduleWindow)
                )
                ScreenTimeAccessibilityService.clearBlockNotification(getApplication())
            }

            if (code.addedTimeMinutes > 0) {
                val dailyLimit = dataRepository.getDailyTimeLimitMinutes()
                val currentAddedTime = dataRepository.getAddedTimeMinutes()

                val usedTimeMillis = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    usageStatsHelper.getTodayUsageTimeMillis()
                } else {
                    0L
                }
                val usedTimeMinutes = TimeManager.millisToMinutes(usedTimeMillis)

                val extraUsed = (usedTimeMinutes - dailyLimit).coerceAtLeast(0)

                val uncoveredDebt = (extraUsed - currentAddedTime).coerceAtLeast(0)

                val totalToAdd = code.addedTimeMinutes + uncoveredDebt

                if (uncoveredDebt > 0) {
                    Log.d("KidLock", "Compensating usage debt: used=$usedTimeMinutes, limit=$dailyLimit, extraUsed=$extraUsed, currentAdded=$currentAddedTime, debt=$uncoveredDebt. Adding $totalToAdd ($code.addedTimeMinutes + $uncoveredDebt)")
                }

                dataRepository.addToAddedTime(totalToAdd)
            }

            _isCodeValid.value = true
            clearCode()
            _message.value = getApplication<Application>().getString(
                R.string.code_activated_format,
                code.addedTimeMinutes
            )
            updateRemainingTime()

            return
        }

        if (dataRepository.verifyPin(codeValue)) {
            // Si une fenêtre de BLOCAGE est active maintenant, le PIN débloque
            // uniquement jusqu'à la fin de CETTE fenêtre (pas la plage suivante).
            // Si aucune fenêtre n'est active, le PIN ouvre le panneau admin.
            val now = Calendar.getInstance()
            val activeWindow = if (dataRepository.isScheduleEnabled() &&
                !ScheduleManager.isScheduleBypassed(dataRepository)
            ) {
                ScheduleManager.getActiveBlockWindow(now, dataRepository)
            } else {
                null
            }

            if (activeWindow != null) {
                val unlockUntil = ScheduleManager.windowEndMillis(now, activeWindow)
                _isCodeValid.value = true
                clearCode()
                _scheduleAdminChoice.value = unlockUntil
                return
            }

            _shouldOpenAdmin.value = true
            clearCode()
            return
        }

        _message.value = getApplication<Application>().getString(R.string.code_not_found_error)
        _isCodeValid.value = false
        // Rate limiting: incrémenter les tentatives
        val attempts = dataRepository.incrementCodeAttempts()
        if (attempts >= 5) {
            dataRepository.setCodeLockoutUntil(System.currentTimeMillis() + 60_000L)
            dataRepository.resetCodeAttempts()
            _message.value = getApplication<Application>().getString(R.string.rate_limit_reached)
        }
    }

    fun updateRemainingTime() {
        viewModelScope.launch(Dispatchers.IO) {
            dataRepository.ensureDailyResetIfNeeded()

            val remaining = usageStatsHelper.getRemainingTimeMinutes(
                dataRepository.getDailyTimeLimitMinutes(),
                dataRepository.getAddedTimeMinutes()
            )

            _remainingTimeMinutes.postValue(remaining)
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun clearShouldOpenAdmin() {
        _shouldOpenAdmin.value = false
    }

    fun clearScheduleAdminChoice() {
        _scheduleAdminChoice.value = null
    }
}
