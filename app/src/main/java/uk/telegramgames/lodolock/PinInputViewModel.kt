package uk.telegramgames.lodolock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class PinInputViewModel(application: Application) : AndroidViewModel(application) {
    private val dataRepository = DataRepository.getInstance(application)

    private val _pinInput = MutableLiveData<String>()
    val pinInput: LiveData<String> = _pinInput

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _isPinCorrect = MutableLiveData<Boolean>()
    val isPinCorrect: LiveData<Boolean> = _isPinCorrect

    init {
        _pinInput.value = ""
    }

    fun setPinInput(pin: String) {
        _pinInput.value = pin
        _errorMessage.value = null
    }

    fun addDigit(digit: Char) {
        val current = _pinInput.value ?: ""
        val filteredDigit = digit.uppercaseChar().takeIf { it.isLetterOrDigit() } ?: return
        val newPin = current + filteredDigit
        _pinInput.value = newPin
        _errorMessage.value = null
    }

    fun removeDigit() {
        val current = _pinInput.value ?: ""
        if (current.isNotEmpty()) {
            _pinInput.value = current.dropLast(1)
            _errorMessage.value = null
        }
    }

    fun clearPin() {
        _pinInput.value = ""
        _errorMessage.value = null
    }

    fun verifyPin(): Boolean {
        // Vérifier le lockout
        if (dataRepository.isPinLockedOut()) {
            val remaining = (dataRepository.getPinLockoutUntil() - System.currentTimeMillis()) / 1000
            _errorMessage.value = getApplication<Application>().getString(R.string.rate_limit_lockout, remaining.toInt())
            _isPinCorrect.value = false
            return false
        }

        val inputPin = (_pinInput.value ?: "").uppercase()

        if (inputPin.isEmpty()) {
            _errorMessage.value = getApplication<Application>().getString(R.string.enter_pin_error)
            _isPinCorrect.value = false
            return false
        }

        if (dataRepository.verifyPin(inputPin)) {
            dataRepository.resetPinAttempts()
            _isPinCorrect.value = true
            _errorMessage.value = null
            return true
        } else {
            val attempts = dataRepository.incrementPinAttempts()
            if (attempts >= 5) {
                dataRepository.setPinLockoutUntil(System.currentTimeMillis() + 60_000L)
                dataRepository.resetPinAttempts()
                _errorMessage.value = getApplication<Application>().getString(R.string.rate_limit_reached)
            } else {
                _errorMessage.value = getApplication<Application>().getString(R.string.wrong_pin_error) + " ($attempts/5)"
            }
            _isPinCorrect.value = false
            clearPin()
            return false
        }
    }
}
