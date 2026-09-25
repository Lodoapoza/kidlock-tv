package uk.telegramgames.kidlock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: CodeInputViewModel
    private lateinit var etCodeInput: EditText
    private lateinit var tvRemainingTime: TextView
    private lateinit var tvBlockReason: TextView
    private lateinit var tvUnlockTime: TextView
    private lateinit var tvMessage: TextView
    private lateinit var btnAdmin: Button

    private var codeInputWatcher: TextWatcher? = null
    private var isUpdatingCodeInput = false
    private var blockReason: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dataRepository = DataRepository.getInstance(this)
        dataRepository.initializeIfNeeded()

        if (!dataRepository.isOnboardingCompleted()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[CodeInputViewModel::class.java]

        etCodeInput = findViewById(R.id.etCodeInput)
        tvRemainingTime = findViewById(R.id.tvRemainingTime)
        tvBlockReason = findViewById(R.id.tvBlockReason)
        tvUnlockTime = findViewById(R.id.tvUnlockTime)
        tvMessage = findViewById(R.id.tvMessage)
        btnAdmin = findViewById(R.id.btnAdmin)

        blockReason = intent.getStringExtra("BLOCK_REASON")

        setupObservers()
        setupCodeInput()
        setupLockScreen()

        btnAdmin.setOnClickListener {
            startActivity(Intent(this, PinInputActivity::class.java))
        }
        btnAdmin.setOnLongClickListener {
            startActivity(Intent(this, PinInputActivity::class.java))
            true
        }

        etCodeInput.post {
            etCodeInput.requestFocus()
        }
    }

    /**
     * Configure l'écran de blocage selon le blockReason.
     * Quand aucune raison n'est fournie (ouverture manuelle / boot), on détermine
     * la VRAIE raison de blocage : jamais de minuteur affiché par défaut.
     */
    private fun setupLockScreen() {
        when (blockReason) {
            "SCHEDULE" -> setupScheduleLock()
            "TIMER" -> setupTimerLock()
            "GUARD" -> setupNeutralScreen(getString(R.string.guard_blocked_message))
            "SERVICE_DISABLED" -> setupNeutralScreen(getString(R.string.service_disabled_message))
            // Ouverte manuellement (launcher), au boot, ou raison inconnue :
            // on détermine la VRAIE raison de blocage — jamais de minuteur par défaut.
            else -> {
                val reason = ScheduleManager.getActiveBlockReason(
                    this, DataRepository.getInstance(this)
                )
                when (reason) {
                    "SCHEDULE" -> setupScheduleLock()
                    "TIMER" -> setupTimerLock()
                    else -> setupIdleLock()
                }
            }
        }
    }

    /**
     * Aucun blocage effectif : écran neutre, pas de faux compteur.
     * Le compteur de temps restant n'a de sens que si un minuteur bloque
     * (setupTimerLock) — on ne l'affiche JAMAIS ici.
     * Si une plage de blocage est configurée, on affiche la prochaine plage
     * pour que l'utilisateur voie que les horaires sont bien actifs.
     */
    private fun setupIdleLock() {
        tvBlockReason.visibility = View.GONE
        tvUnlockTime.visibility = View.GONE
        tvRemainingTime.visibility = View.GONE
        findViewById<TextView>(R.id.tvCodeLabel).text = getString(R.string.enter_code)

        val repo = DataRepository.getInstance(this)
        if (repo.isScheduleEnabled() && repo.getTimeWindows().isNotEmpty() && !ScheduleManager.isScheduleBypassed(repo)) {
            val nextStart = ScheduleManager.getNextBlockWindowStart(
                java.util.Calendar.getInstance(), repo
            )
            if (nextStart != null) {
                tvUnlockTime.text = getString(
                    R.string.lock_schedule_next_window,
                    TimeManager.formatTimeOfDay(nextStart)
                )
                tvUnlockTime.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Écran neutre sans compteur ni raison (guard, service désactivé…).
     */
    private fun setupNeutralScreen(message: String? = null) {
        tvBlockReason.visibility = View.GONE
        tvUnlockTime.visibility = View.GONE
        tvRemainingTime.visibility = View.GONE
        findViewById<TextView>(R.id.tvCodeLabel).text = getString(R.string.enter_code)
        tvMessage.text = message ?: ""
        tvMessage.visibility = if (message.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun setupScheduleLock() {
        // Raison
        tvBlockReason.text = getString(R.string.lock_schedule_reason)
        tvBlockReason.visibility = View.VISIBLE

        // Timer masqué (pas pertinent en schedule)
        tvRemainingTime.visibility = View.GONE

        // Heure de déblocage
        val nextAllowed = ScheduleManager.nextBlockedStartMillis(
            java.util.Calendar.getInstance(),
            DataRepository.getInstance(this)
        )
        val unlockTimeStr = TimeManager.formatTimeOfDay(nextAllowed)
        tvUnlockTime.text = getString(R.string.lock_schedule_unlock_time, unlockTimeStr)
        tvUnlockTime.visibility = View.VISIBLE

        // Action
        findViewById<TextView>(R.id.tvCodeLabel).text = getString(R.string.enter_code_schedule)
    }

    private fun setupTimerLock() {
        // Raison
        tvBlockReason.text = getString(R.string.lock_timer_reason)
        tvBlockReason.visibility = View.VISIBLE

        // Timer visible
        tvRemainingTime.visibility = View.VISIBLE

        // Heure de déblocage (demain)
        tvUnlockTime.text = getString(R.string.lock_timer_unlock_time)
        tvUnlockTime.visibility = View.VISIBLE
    }

    private fun setupObservers() {
        viewModel.codeInput.observe(this) { code ->
            displayCode(code)
        }

        viewModel.remainingTimeMinutes.observe(this) { minutes ->
            tvRemainingTime.text = getString(R.string.remaining_time_format, TimeManager.formatMinutes(this, minutes))
        }

        viewModel.message.observe(this) { message ->
            tvMessage.text = message ?: ""
            tvMessage.visibility = if (message.isNullOrEmpty()) View.GONE else View.VISIBLE
            if (!message.isNullOrEmpty()) {
                tvMessage.postDelayed({
                    if (tvMessage.text.toString() == message) {
                        tvMessage.visibility = View.GONE
                    }
                }, 4_000L)
            }
        }

        viewModel.isCodeValid.observe(this) { isValid ->
            if (isValid) {
                tvMessage.setTextColor(getColor(R.color.status_good))
            } else {
                tvMessage.setTextColor(getColor(R.color.status_bad))
            }
        }

        viewModel.shouldOpenAdmin.observe(this) { shouldOpen ->
            if (shouldOpen) {
                startActivity(Intent(this, AdminActivity::class.java))
                viewModel.clearShouldOpenAdmin()
            }
        }

        viewModel.scheduleAdminChoice.observe(this) { unlockUntil ->
            if (unlockUntil != null && unlockUntil > 0L) {
                showScheduleAdminChoice(unlockUntil)
                viewModel.clearScheduleAdminChoice()
            }
        }
    }

    private fun showScheduleAdminChoice(unlockUntil: Long) {
        val repository = DataRepository.getInstance(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.schedule_admin_choice_title)
            .setMessage(R.string.schedule_admin_choice_message)
            .setNegativeButton(R.string.schedule_admin_choice_admin) { _, _ ->
                startActivity(Intent(this, AdminActivity::class.java))
            }
            .setPositiveButton(R.string.schedule_admin_choice_unlock) { _, _ ->
                repository.setScheduleUnlockUntil(unlockUntil)
                ScreenTimeAccessibilityService.clearBlockNotification(this)
                finishAffinity()
            }
            .show()
    }

    private fun addChar(char: Char) {
        viewModel.addCharToCode(char)
    }

    private fun removeChar() {
        viewModel.removeCharFromCode()
    }

    private fun displayCode(code: String) {
        if (!isUpdatingCodeInput && etCodeInput.text.toString() != code) {
            isUpdatingCodeInput = true
            etCodeInput.removeTextChangedListener(codeInputWatcher)
            etCodeInput.setText(code)
            etCodeInput.setSelection(code.length)
            etCodeInput.addTextChangedListener(codeInputWatcher)
            isUpdatingCodeInput = false
        }
    }

    private fun activateCode() {
        viewModel.activateCode()
    }

    private fun setupCodeInput() {
        codeInputWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingCodeInput) return

                val text = s?.toString()?.filter { it.isDigit() } ?: ""

                val limitedText = if (text.length > 6) text.substring(0, 6) else text
                
                val currentCode = viewModel.codeInput.value ?: ""
                
                if (limitedText != currentCode) {
                    val oldCode = viewModel.codeInput.value ?: ""
                    repeat(oldCode.length) {
                        removeChar()
                    }
                    limitedText.forEach { char ->
                        addChar(char)
                    }
                }
            }
        }
        
        etCodeInput.addTextChangedListener(codeInputWatcher)

        etCodeInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || 
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                activateCode()
                true
            } else {
                false
            }
        }
        
        etCodeInput.setOnFocusChangeListener { view, hasFocus ->
            etCodeInput.setCursorVisible(hasFocus)
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            if (hasFocus) {
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateRemainingTime()
        refreshLockScreen()
        
        etCodeInput.post {
            if (!etCodeInput.hasFocus()) {
                etCodeInput.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(etCodeInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            blockReason = intent.getStringExtra("BLOCK_REASON")
            refreshLockScreen()
        }
    }

    private fun refreshLockScreen() {
        val repo = DataRepository.getInstance(this)
        val actualReason = if (blockReason == "SERVICE_DISABLED") {
            "SERVICE_DISABLED"
        } else {
            ScheduleManager.getActiveBlockReason(this, repo)
        }
        when (actualReason) {
            "SCHEDULE" -> setupScheduleLock()
            "TIMER" -> setupTimerLock()
            "SERVICE_DISABLED" -> setupNeutralScreen(getString(R.string.service_disabled_message))
            else -> setupIdleLock()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_DOWN) {
            // Обработка навигации пультом ДУ
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP, 
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    // При навигации вниз с EditText скрываем клавиатуру для выбора кнопок
                    val currentFocus = currentFocus
                    if (currentFocus == etCodeInput && event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(etCodeInput.windowToken, 0)
                    }
                    // Разрешаем стандартную обработку навигации
                    return super.dispatchKeyEvent(event)
                }
            }
            
            // Обработка ввода цифр с клавиатуры или пульта ДУ
            when (event.keyCode) {
                KeyEvent.KEYCODE_DEL -> {
                    removeChar()
                    return true
                }
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    activateCode()
                    return true
                }
                KeyEvent.KEYCODE_0 -> { addChar('0'); return true }
                KeyEvent.KEYCODE_1 -> { addChar('1'); return true }
                KeyEvent.KEYCODE_2 -> { addChar('2'); return true }
                KeyEvent.KEYCODE_3 -> { addChar('3'); return true }
                KeyEvent.KEYCODE_4 -> { addChar('4'); return true }
                KeyEvent.KEYCODE_5 -> { addChar('5'); return true }
                KeyEvent.KEYCODE_6 -> { addChar('6'); return true }
                KeyEvent.KEYCODE_7 -> { addChar('7'); return true }
                KeyEvent.KEYCODE_8 -> { addChar('8'); return true }
                KeyEvent.KEYCODE_9 -> { addChar('9'); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
