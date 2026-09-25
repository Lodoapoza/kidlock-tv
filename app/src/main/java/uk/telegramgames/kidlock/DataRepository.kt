package uk.telegramgames.kidlock

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.security.MessageDigest

class DataRepository private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    init {
        migrateLegacyBlockingFlag()
        sanitizeScheduleState()
    }

    /**
     * État incohérent : schedule activé sans aucune plage configurée ne bloque rien.
     * On le désactive pour que l'UI (switch « Blocage ») reflète la réalité.
     */
    private fun sanitizeScheduleState() {
        if (isScheduleEnabled() && getTimeWindows().isEmpty()) {
            Log.d("KidLock", "Migration: schedule_enabled=true sans plage -> désactivation")
            setScheduleEnabled(false)
        }
    }

    /**
     * Migration depuis l'ancienne version :
     * - `blocking_enabled` était un flag global stocké (aujourd'hui dérivé).
     * - Si l'utilisateur l'avait activé mais que `timer_enabled` n'existe pas encore,
     *   on active le timer pour conserver le blocage.
     */
    private fun migrateLegacyBlockingFlag() {
        if (!prefs.contains(KEY_TIMER_ENABLED) && prefs.getBoolean(KEY_BLOCKING_ENABLED, false)) {
            Log.d("KidLock", "Migration: blocking_enabled=true -> timer_enabled=true")
            prefs.edit().putBoolean(KEY_TIMER_ENABLED, true).apply()
        }
        if (prefs.contains(KEY_BLOCKING_ENABLED)) {
            prefs.edit().remove(KEY_BLOCKING_ENABLED).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "kidlock_prefs"
        private const val KEY_PIN = "pin"
        private const val KEY_DAILY_LIMIT = "daily_limit_minutes"
        private const val KEY_CODES = "codes"
        private const val KEY_REMAINING_TIME = "remaining_time_minutes"
        private const val KEY_LAST_RESET_DATE = "last_reset_date"
        private const val KEY_ADDED_TIME = "added_time_minutes"
        private const val KEY_AUTOSTART_ENABLED = "autostart_enabled"
        private const val KEY_BLOCKING_ENABLED = "blocking_enabled"
        private const val KEY_PAID_VERSION = "paid_version"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_UNLOCKED_UNTIL_TOMORROW = "unlocked_until_tomorrow"

        // Schedule (time windows) keys
        private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        private const val KEY_TIME_WINDOWS = "time_windows_json"
        private const val KEY_SCHEDULE_UNLOCK_UNTIL = "schedule_unlock_until"
        private const val MAX_TIME_WINDOWS = 10

        // Timer mode key
        private const val KEY_TIMER_ENABLED = "timer_enabled"

        // Rate limiting keys
        private const val KEY_PIN_ATTEMPTS = "pin_attempts"
        private const val KEY_PIN_LOCKOUT_UNTIL = "pin_lockout_until"
        private const val KEY_CODE_ATTEMPTS = "code_attempts"
        private const val KEY_CODE_LOCKOUT_UNTIL = "code_lockout_until"

        private const val DEFAULT_PIN = "000000"
        private const val PIN_HASH_PREFIX = "sha256:"
        private const val MAX_PIN_ATTEMPTS = 5
        private const val MAX_CODE_ATTEMPTS = 5
        private const val PIN_LOCKOUT_DURATION_MS = 60_000L // 60 seconds
        private const val CODE_LOCKOUT_DURATION_MS = 60_000L // 60 seconds

        @Volatile
        private var INSTANCE: DataRepository? = null

        fun getInstance(context: Context): DataRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = DataRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    fun getPin(): String {
        return prefs.getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN
    }

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN, hashPin(pin)).apply()
    }

    /**
     * Hash PIN en SHA-256 pour le stockage sécurisé.
     * Retourne le hash hexadécimal.
     */
    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray())
        return PIN_HASH_PREFIX + hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Vérifie si le PIN entré correspond au PIN stocké.
     * Supporte la migration: compare le hash ET l'ancien plaintext.
     */
    fun verifyPin(inputPin: String): Boolean {
        val storedPin = getPin()
        if (storedPin.startsWith(PIN_HASH_PREFIX)) {
            return MessageDigest.isEqual(
                storedPin.removePrefix(PIN_HASH_PREFIX).toByteArray(Charsets.US_ASCII),
                hashPin(inputPin).removePrefix(PIN_HASH_PREFIX).toByteArray(Charsets.US_ASCII)
            )
        }

        // Migration d'une ancienne installation qui stockait le PIN en clair.
        if (MessageDigest.isEqual(inputPin.toByteArray(Charsets.UTF_8), storedPin.toByteArray(Charsets.UTF_8))) {
            setPin(inputPin)
            return true
        }
        return false
    }

    // --- Rate limiting ---

    fun getPinAttempts(): Int {
        return prefs.getInt(KEY_PIN_ATTEMPTS, 0)
    }

    fun incrementPinAttempts(): Int {
        val current = getPinAttempts() + 1
        prefs.edit().putInt(KEY_PIN_ATTEMPTS, current).apply()
        return current
    }

    fun resetPinAttempts() {
        prefs.edit().putInt(KEY_PIN_ATTEMPTS, 0).apply()
    }

    fun getPinLockoutUntil(): Long {
        return prefs.getLong(KEY_PIN_LOCKOUT_UNTIL, 0)
    }

    fun setPinLockoutUntil(timestamp: Long) {
        prefs.edit().putLong(KEY_PIN_LOCKOUT_UNTIL, timestamp).apply()
    }

    fun isPinLockedOut(): Boolean {
        return System.currentTimeMillis() < getPinLockoutUntil()
    }

    fun getCodeAttempts(): Int {
        return prefs.getInt(KEY_CODE_ATTEMPTS, 0)
    }

    fun incrementCodeAttempts(): Int {
        val current = getCodeAttempts() + 1
        prefs.edit().putInt(KEY_CODE_ATTEMPTS, current).apply()
        return current
    }

    fun resetCodeAttempts() {
        prefs.edit().putInt(KEY_CODE_ATTEMPTS, 0).apply()
    }

    fun getCodeLockoutUntil(): Long {
        return prefs.getLong(KEY_CODE_LOCKOUT_UNTIL, 0)
    }

    fun setCodeLockoutUntil(timestamp: Long) {
        prefs.edit().putLong(KEY_CODE_LOCKOUT_UNTIL, timestamp).apply()
    }

    fun isCodeLockedOut(): Boolean {
        return System.currentTimeMillis() < getCodeLockoutUntil()
    }

    fun isOnboardingCompleted(): Boolean {
        val savedFlag = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        return savedFlag || getPin() != DEFAULT_PIN
    }

    fun setOnboardingCompleted() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
    }

    fun getDailyTimeLimitMinutes(): Int {
        return prefs.getInt(KEY_DAILY_LIMIT, 60)
    }

    fun setDailyTimeLimitMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_DAILY_LIMIT, minutes).apply()
    }

    fun getCodes(): List<Code> {
        val codesJson = prefs.getString(KEY_CODES, "") ?: ""
        Log.d("KidLock", "DataRepository.getCodes() - codesJson length: ${codesJson.length}")
        if (codesJson.isEmpty()) {
            Log.d("KidLock", "DataRepository.getCodes() - empty")
            return emptyList()
        }

        val codes = codesJson.split(";")
            .mapNotNull { Code.fromJsonString(it) }
        Log.d("KidLock", "DataRepository.getCodes() - parsed ${codes.size} codes")
        return codes
    }

    fun saveCodes(codes: List<Code>) {
        val codesJson = codes.joinToString(";") { it.toJsonString() }
        Log.d("KidLock", "DataRepository.saveCodes() - saving ${codes.size} codes")
        val success = prefs.edit().putString(KEY_CODES, codesJson).commit()
        if (!success) {
            Log.w("KidLock", "DataRepository.saveCodes() - commit failed, falling back to apply()")
            prefs.edit().putString(KEY_CODES, codesJson).apply()
        } else {
            Log.d("KidLock", "DataRepository.saveCodes() - коды успешно сохранены через commit")
        }
    }

    fun addCode(code: Code) {
        val codes = getCodes().toMutableList()
        codes.add(code)
        saveCodes(codes)
    }

    fun updateCode(updatedCode: Code) {
        val codes = getCodes().toMutableList()
        val index = codes.indexOfFirst { it.value == updatedCode.value }
        if (index >= 0) {
            codes[index] = updatedCode
            saveCodes(codes)
        }
    }

    fun findCode(value: String): Code? {
        return getCodes().firstOrNull { it.value == value }
    }

    fun getRemainingTimeMinutes(): Int {
        return prefs.getInt(KEY_REMAINING_TIME, 0)
    }

    fun setRemainingTimeMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_REMAINING_TIME, minutes).apply()
    }

    fun addTimeMinutes(minutes: Int) {
        val current = getRemainingTimeMinutes()
        setRemainingTimeMinutes(current + minutes)
    }

    fun resetRemainingTime() {
        setRemainingTimeMinutes(0)
    }

    fun getLastResetDate(): Long {
        return prefs.getLong(KEY_LAST_RESET_DATE, 0)
    }

    fun setLastResetDate(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_RESET_DATE, timestamp).apply()
    }

    fun getAddedTimeMinutes(): Int {
        return prefs.getInt(KEY_ADDED_TIME, 0)
    }

    fun setAddedTimeMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_ADDED_TIME, minutes).apply()
    }

    fun addToAddedTime(minutes: Int) {
        val current = getAddedTimeMinutes()
        setAddedTimeMinutes(current + minutes)
    }

    fun resetAddedTime() {
        setAddedTimeMinutes(0)
    }

    fun isAutostartEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTOSTART_ENABLED, false)
    }

    fun setAutostartEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOSTART_ENABLED, enabled).apply()
    }

    fun isBlockingEnabled(): Boolean {
        return isScheduleEnabled() || isTimerEnabled()
    }

    /**
     * Active/désactive le blocage global en pilotant les vrais modes.
     * ON : schedule si des plages existent, sinon timer.
     * OFF : désactive les deux.
     * Ne JAMAIS activer le minuteur en silence quand l'utilisateur a des plages.
     */
    fun setBlockingEnabled(enabled: Boolean) {
        if (enabled) {
            if (getTimeWindows().isNotEmpty()) {
                setScheduleEnabled(true)
                setTimerEnabled(false)
            } else {
                setScheduleEnabled(false)
                setTimerEnabled(true)
            }
        } else {
            setScheduleEnabled(false)
            setTimerEnabled(false)
        }
    }

    fun isPaidVersion(): Boolean {
        return prefs.getBoolean(KEY_PAID_VERSION, false)
    }

    fun setPaidVersion(isPaid: Boolean) {
        prefs.edit().putBoolean(KEY_PAID_VERSION, isPaid).apply()
    }

    fun isUnlockedUntilTomorrow(): Boolean {
        return prefs.getBoolean(KEY_UNLOCKED_UNTIL_TOMORROW, false)
    }

    fun setUnlockedUntilTomorrow(unlocked: Boolean) {
        prefs.edit().putBoolean(KEY_UNLOCKED_UNTIL_TOMORROW, unlocked).apply()
    }

    // Schedule getters/setters
    fun isScheduleEnabled(): Boolean {
        return prefs.getBoolean(KEY_SCHEDULE_ENABLED, false)
    }

    fun setScheduleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCHEDULE_ENABLED, enabled).apply()
        if (!enabled) {
            setScheduleUnlockUntil(0L)
        }
    }

    fun isTimerEnabled(): Boolean {
        // Défaut = false : rien de configuré ⇒ aucun blocage.
        // Le timer doit être activé explicitement (onboarding ou admin).
        return prefs.getBoolean(KEY_TIMER_ENABLED, false)
    }

    fun setTimerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TIMER_ENABLED, enabled).apply()
    }

    fun getTimeWindows(): List<TimeWindow> {
        val json = prefs.getString(KEY_TIME_WINDOWS, "") ?: ""
        return TimeWindow.listFromJson(json)
    }

    fun saveTimeWindows(windows: List<TimeWindow>) {
        val json = TimeWindow.listToJson(windows)
        prefs.edit()
            .putString(KEY_TIME_WINDOWS, json)
            .putLong(KEY_SCHEDULE_UNLOCK_UNTIL, 0L)
            .apply()
    }

    fun addTimeWindow(window: TimeWindow): Boolean {
        val current = getTimeWindows()
        if (current.size >= MAX_TIME_WINDOWS) return false
        saveTimeWindows(current + window)
        if (!isScheduleEnabled()) setScheduleEnabled(true)
        return true
    }

    fun updateTimeWindow(updated: TimeWindow) {
        val current = getTimeWindows().toMutableList()
        val index = current.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            current[index] = updated
            saveTimeWindows(current)
        }
    }

    fun removeTimeWindow(id: String) {
        val current = getTimeWindows().toMutableList()
        current.removeAll { it.id == id }
        saveTimeWindows(current)
        if (current.isEmpty() && isScheduleEnabled()) setScheduleEnabled(false)
    }

    fun canAddTimeWindow(newWindow: TimeWindow, excludeId: String? = null): Boolean {
        val current = getTimeWindows()
        if (current.size >= MAX_TIME_WINDOWS) return false
        return current
            .filter { it.id != excludeId }
            .none { it.overlaps(newWindow) }
    }

    fun getMaxTimeWindows(): Int = MAX_TIME_WINDOWS

    fun getScheduleUnlockUntil(): Long {
        return prefs.getLong(KEY_SCHEDULE_UNLOCK_UNTIL, 0)
    }

    fun setScheduleUnlockUntil(timestamp: Long) {
        prefs.edit().putLong(KEY_SCHEDULE_UNLOCK_UNTIL, timestamp).apply()
    }

    fun generateCodes(minutesPerCode: Int, count: Int): List<Code> {
        Log.d("KidLock", "DataRepository.generateCodes() called: minutesPerCode=$minutesPerCode, count=$count")

        val oldCodesCount = getCodes().size
        Log.d("KidLock", "DataRepository.generateCodes() - clearing old codes: $oldCodesCount items")
        saveCodes(emptyList())
        
        val codes = generateCodesInternal(List(count) { minutesPerCode })

        Log.d("KidLock", "DataRepository.generateCodes() - saving ${codes.size} new codes")
        saveCodes(codes)
        Log.d("KidLock", "DataRepository.generateCodes() - generated ${codes.size} codes")
        return codes
    }

    fun generateCodesWithMinutes(minutesList: List<Int>): List<Code> {
        Log.d("KidLock", "DataRepository.generateCodesWithMinutes() called: minutesList=$minutesList")

        val oldCodesCount = getCodes().size
        Log.d("KidLock", "DataRepository.generateCodesWithMinutes() - clearing old codes: $oldCodesCount items")
        saveCodes(emptyList())

        val codes = generateCodesInternal(minutesList)
        Log.d("KidLock", "DataRepository.generateCodesWithMinutes() - saving ${codes.size} new codes")
        saveCodes(codes)
        Log.d("KidLock", "DataRepository.generateCodesWithMinutes() - generated ${codes.size} codes")
        return codes
    }

    fun resetDailyData() {
        val existingScheduleBypass = getScheduleUnlockUntil()
        resetRemainingTime()
        resetAddedTime()
        setUnlockedUntilTomorrow(false)
        if (existingScheduleBypass <= System.currentTimeMillis()) {
            setScheduleUnlockUntil(0L)
        }
        setLastResetDate(TimeManager.getTodayStartTime())
    }

    fun ensureDailyResetIfNeeded(): Boolean {
        val lastReset = getLastResetDate()
        return if (TimeManager.shouldResetDailyLimit(lastReset)) {
            resetDailyData()
            true
        } else {
            false
        }
    }

    fun initializeIfNeeded() {
        if (getLastResetDate() == 0L) {
            setLastResetDate(TimeManager.getTodayStartTime())
        }
        val currentPin = getPin()
        if (currentPin.isEmpty()) {
            setPin(DEFAULT_PIN)
        }
        
        if (currentPin.length == 4) {
            Log.d("KidLock", "Migration: resetting obsolete 4-digit PIN to $DEFAULT_PIN")
            setPin(DEFAULT_PIN)
        }
        
        // Миграция: очищаем устаревшие 4-значные коды
        val codes = getCodes()
        if (codes.isNotEmpty() && codes.any { it.value.length == 4 }) {
            Log.d("KidLock", "Migration: clearing obsolete 4-digit codes (${codes.count { it.value.length == 4 }} found)")
            saveCodes(emptyList())
        }
    }

    private fun generateCodesInternal(minutesList: List<Int>): List<Code> {
        val chars = "0123456789"
        val codes = mutableListOf<Code>()
        val existing = mutableSetOf<String>()
        minutesList.forEach { minutes ->
            var codeValue: String
            do {
                codeValue = (1..6).map { chars.random() }.joinToString("")
            } while (existing.contains(codeValue) || verifyPin(codeValue))

            existing.add(codeValue)
            codes.add(Code(value = codeValue, addedTimeMinutes = minutes))
            Log.d("KidLock", "DataRepository.generateCodesInternal() - generated code for ${minutes} min")
        }

        return codes
    }
}
