package uk.telegramgames.kidlock

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import uk.telegramgames.kidlock.BuildConfig

class AdminActivity : AppCompatActivity() {
    private lateinit var viewModel: AdminViewModel

    private lateinit var btnExit: Button
    private lateinit var rvSections: RecyclerView
    private lateinit var sectionAdapter: AdminSectionAdapter

    private val tabButtons = mutableListOf<Button>()
    private val tabSectionIds = mutableListOf<Int>()
    
    private lateinit var layoutSectionDashboard: View
    private lateinit var layoutSectionSettings: View
    private lateinit var layoutSectionAccess: View
    private lateinit var layoutSectionSystem: View
    private lateinit var layoutSectionSchedule: View
    private lateinit var layoutSectionAbout: View
    
    // Schedule UI
    private lateinit var switchScheduleEnabled: Switch
    private lateinit var rvTimeWindows: RecyclerView
    private lateinit var tvNoWindows: TextView
    private lateinit var btnAddWindow: Button
    private lateinit var timeWindowAdapter: TimeWindowAdapter
    
    // Device Admin UI
    private lateinit var tvDeviceAdminStatus: TextView
    private lateinit var btnActivateDeviceAdmin: Button
    
    private lateinit var tvRemainingTime: TextView
    private lateinit var tvLockStatus: TextView
    private lateinit var btnUnlock: Button
    
    private lateinit var btnSetLimit: Button
    private lateinit var switchAutostart: Switch
    private lateinit var switchBlocking: Switch
    private lateinit var switchTimerEnabled: Switch
    
    private lateinit var etCodeCount: EditText
    private lateinit var etMinutesPerCode: EditText
    private lateinit var btnGenerateCodes: Button
    private lateinit var etNewPin: EditText
    private lateinit var btnChangePin: Button
    private lateinit var rvCodes: RecyclerView
    private lateinit var codeAdapter: CodeAdapter
    
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvAccessibilityInstructions: TextView
    private lateinit var btnOpenAccessibilitySettings: Button
    private lateinit var tvUsageStatsStatus: TextView
    private lateinit var tvUsageStatsInstructions: TextView
    private lateinit var btnOpenUsageStatsSettings: Button
    
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvOverlayInstructions: TextView
    private lateinit var btnOpenOverlaySettings: Button
    
    private lateinit var tvAboutVersion: TextView
    
    private lateinit var tvMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        viewModel = ViewModelProvider(this)[AdminViewModel::class.java]

        initViews()
        setupSections()
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        
        Log.d("KidLock", "AdminActivity.onCreate() - loadCodes called")
        viewModel.loadCodes()

        setupPrimaryButtons()
    }

    private fun setupPrimaryButtons() {
        setupPrimaryButton(btnSetLimit)
        setupPrimaryButton(btnUnlock)
        
        setupPrimaryButton(btnGenerateCodes)
        setupPrimaryButton(btnChangePin)
        
        setupPrimaryButton(btnOpenAccessibilitySettings)
        setupPrimaryButton(btnOpenUsageStatsSettings)
        setupPrimaryButton(btnOpenOverlaySettings)
        
        setupPrimaryButton(btnExit)
    }

    private fun setupPrimaryButton(button: Button) {
        button.backgroundTintList = null
        button.isFocusable = true
        button.isClickable = true
    }

    private fun initViews() {
        btnExit = findViewById(R.id.btnExit)

        rvSections = findViewById(R.id.rvSections)

        layoutSectionDashboard = findViewById(R.id.layout_section_dashboard)
        layoutSectionSettings = findViewById(R.id.layout_section_settings)
        layoutSectionAccess = findViewById(R.id.layout_section_access)
        layoutSectionSystem = findViewById(R.id.layout_section_system)
        layoutSectionSchedule = findViewById(R.id.layout_section_schedule)
        layoutSectionAbout = findViewById(R.id.layout_section_about)

        tvRemainingTime = findViewById(R.id.tvRemainingTime)
        tvLockStatus = findViewById(R.id.tvLockStatus)
        btnUnlock = findViewById(R.id.btnUnlock)

        btnSetLimit = findViewById(R.id.btnSetLimit)
        switchAutostart = findViewById(R.id.switchAutostart)
        switchBlocking = findViewById(R.id.switchBlocking)
        switchTimerEnabled = findViewById(R.id.switchTimerEnabled)

        etCodeCount = findViewById(R.id.etCodeCount)
        etMinutesPerCode = findViewById(R.id.etMinutesPerCode)
        btnGenerateCodes = findViewById(R.id.btnGenerateCodes)
        etNewPin = findViewById(R.id.etNewPin)
        btnChangePin = findViewById(R.id.btnChangePin)
        rvCodes = findViewById(R.id.rvCodes)

        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvAccessibilityInstructions = findViewById(R.id.tvAccessibilityInstructions)
        btnOpenAccessibilitySettings = findViewById(R.id.btnOpenAccessibilitySettings)
        tvUsageStatsStatus = findViewById(R.id.tvUsageStatsStatus)
        tvUsageStatsInstructions = findViewById(R.id.tvUsageStatsInstructions)
        btnOpenUsageStatsSettings = findViewById(R.id.btnOpenUsageStatsSettings)

        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvOverlayInstructions = findViewById(R.id.tvOverlayInstructions)
        btnOpenOverlaySettings = findViewById(R.id.btnOpenOverlaySettings)

        tvAboutVersion = findViewById(R.id.tvAboutVersion)

        // Schedule UI
        switchScheduleEnabled = findViewById(R.id.switchScheduleEnabled)
        rvTimeWindows = findViewById(R.id.rvTimeWindows)
        tvNoWindows = findViewById(R.id.tvNoWindows)
        btnAddWindow = findViewById(R.id.btnAddWindow)

        // Device Admin UI
        tvDeviceAdminStatus = findViewById(R.id.tvDeviceAdminStatus)
        btnActivateDeviceAdmin = findViewById(R.id.btnActivateDeviceAdmin)
        
        // Set app version
        tvAboutVersion.text = getString(R.string.about_version_format, BuildConfig.VERSION_NAME)

        tvMessage = findViewById(R.id.tvMessage)

        setupTabs()

        setupEditTextKeyboard(etCodeCount)
        setupEditTextKeyboard(etMinutesPerCode)
        setupEditTextKeyboard(etNewPin)
    }

    private fun setupTabs() {
        val tabIds = listOf(
            R.id.tab_schedule to 7,
            R.id.tab_dashboard to 1,
            R.id.tab_access to 3,
            R.id.tab_settings to 2,
            R.id.tab_system to 4,
            R.id.tab_about to 6
        )
        for ((tabId, sectionId) in tabIds) {
            val view = findViewById<View>(tabId) ?: continue
            val tab = view as? Button ?: continue
            tab.isSelected = false
            tab.setOnClickListener {
                updateSectionVisibility(sectionId)
            }
            tabButtons.add(tab)
            tabSectionIds.add(sectionId)
        }
    }

    private fun highlightActiveTab(sectionId: Int) {
        for (i in tabButtons.indices) {
            tabButtons[i].isSelected = tabSectionIds[i] == sectionId
        }
    }

    private fun setupEditTextKeyboard(editText: EditText) {
        editText.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        editText.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun setupSections() {
        // Ordre voulu : Horaires (7) en premier, puis Monitoring/Dashboard (1),
        // puis Accès, Paramètres, Système, À propos, Quitter.
        val sections = listOf(
            AdminSection(7, getString(R.string.section_schedule), true),
            AdminSection(1, getString(R.string.section_dashboard), true),
            AdminSection(3, getString(R.string.section_access)),
            AdminSection(2, getString(R.string.section_settings)),
            AdminSection(4, getString(R.string.section_system)),
            AdminSection(6, getString(R.string.section_about)),
            AdminSection(5, getString(R.string.exit))
        )

        sectionAdapter = AdminSectionAdapter(sections) { section ->
            if (section.id == 5) {
                finish()
            } else {
                updateSectionVisibility(section.id)
            }
        }

        rvSections.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        rvSections.adapter = sectionAdapter

        updateSectionVisibility(7)
    }

    private fun updateSectionVisibility(sectionId: Int) {
        layoutSectionDashboard.visibility = if (sectionId == 1) View.VISIBLE else View.GONE
        layoutSectionSettings.visibility = if (sectionId == 2) View.VISIBLE else View.GONE
        layoutSectionAccess.visibility = if (sectionId == 3) View.VISIBLE else View.GONE
        layoutSectionSystem.visibility = if (sectionId == 4) View.VISIBLE else View.GONE
layoutSectionSchedule.visibility = if (sectionId == 7) View.VISIBLE else View.GONE
        layoutSectionAbout.visibility = if (sectionId == 6) View.VISIBLE else View.GONE

        highlightActiveTab(sectionId)

        when (sectionId) {
            1 -> btnSetLimit.requestFocus()
            2 -> etNewPin.requestFocus()
            3 -> {
                val hasCodes = (viewModel.codes.value?.isNotEmpty() == true)
                if (hasCodes) {
                    rvCodes.requestFocus()
                } else {
                    val isPaid = viewModel.isPaidVersion.value ?: false
                    if (isPaid) {
                        etCodeCount.requestFocus()
                    } else {
                        etMinutesPerCode.requestFocus()
                    }
                }
            }
            4 -> btnOpenAccessibilitySettings.requestFocus()
            7 -> btnAddWindow.requestFocus()
        }
    }

    private fun setupRecyclerView() {
        codeAdapter = CodeAdapter(emptyList()) { code ->
            viewModel.deleteCode(code)
        }
        rvCodes.layoutManager = LinearLayoutManager(this)
        rvCodes.adapter = codeAdapter

        timeWindowAdapter = TimeWindowAdapter(
            windows = emptyList(),
            onEdit = { window -> showEditTimeWindowDialog(window) },
            onDelete = { window -> confirmDeleteTimeWindow(window) }
        )
        rvTimeWindows.layoutManager = LinearLayoutManager(this)
        rvTimeWindows.adapter = timeWindowAdapter
    }

    private fun setupObservers() {
        viewModel.dailyLimitMinutes.observe(this) { minutes ->
            tvRemainingTime.text = TimeManager.formatMinutes(this, minutes)
        }

        viewModel.remainingTimeMinutes.observe(this) { minutes ->
            val statusColor = if (minutes > 0) getColor(R.color.status_good) else getColor(R.color.status_bad)
            val statusText = if (minutes > 0) {
                getString(R.string.time_remaining_format, TimeManager.formatMinutes(this, minutes))
            } else {
                getString(R.string.time_expired)
            }
            tvLockStatus.text = statusText
            tvLockStatus.setTextColor(statusColor)
        }

        viewModel.codes.observe(this) { codes ->
            codeAdapter.updateCodes(codes)
            if (codes.isNotEmpty()) {
                rvCodes.scrollToPosition(0)
            }
        }

        viewModel.isAccessibilityServiceEnabled.observe(this) { enabled ->
            val statusText = if (enabled) getString(R.string.accessibility_enabled) else getString(R.string.accessibility_disabled)
            tvAccessibilityStatus.text = getString(R.string.accessibility_status_format, statusText)
            tvAccessibilityStatus.setTextColor(if (enabled) getColor(R.color.status_good) else getColor(R.color.status_bad))
            tvAccessibilityInstructions.visibility = if (enabled) View.GONE else View.VISIBLE
        }

        viewModel.isUsageStatsPermissionGranted.observe(this) { granted ->
            val statusText = if (granted) getString(R.string.usage_stats_granted) else getString(R.string.usage_stats_denied)
            tvUsageStatsStatus.text = getString(R.string.usage_stats_status_format, statusText)
            tvUsageStatsStatus.setTextColor(if (granted) getColor(R.color.status_good) else getColor(R.color.status_bad))
            tvUsageStatsInstructions.visibility = if (granted) View.GONE else View.VISIBLE
        }

        viewModel.isOverlayPermissionGranted.observe(this) { granted ->
            val statusText = if (granted) getString(R.string.overlay_granted) else getString(R.string.overlay_denied)
            tvOverlayStatus.text = getString(R.string.overlay_status_format, statusText)
            tvOverlayStatus.setTextColor(if (granted) getColor(R.color.status_good) else getColor(R.color.status_bad))
            tvOverlayInstructions.visibility = if (granted) View.GONE else View.VISIBLE
        }

        viewModel.isAutostartEnabled.observe(this) { enabled ->
            switchAutostart.isChecked = enabled
        }

        viewModel.isBlockingEnabled.observe(this) { enabled ->
            switchBlocking.isChecked = enabled
        }

        viewModel.canEnableBlocking.observe(this) { canEnable ->
            switchBlocking.isEnabled = canEnable
            if (!canEnable && switchBlocking.isChecked) {
                switchBlocking.isChecked = false
            }
        }

        viewModel.isTimerEnabled.observe(this) { enabled ->
            switchTimerEnabled.isChecked = enabled
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

        viewModel.isPaidVersion.observe(this) { isPaid ->
            updateCodeGenerationUI(isPaid)
        }

        viewModel.isUnlockedUntilTomorrow.observe(this) { isUnlocked ->
            btnUnlock.text = if (isUnlocked) {
                getString(R.string.lock_until_tomorrow)
            } else {
                getString(R.string.unlock_until_tomorrow)
            }
        }

        // Schedule observations
        viewModel.isScheduleEnabled.observe(this) { enabled ->
            switchScheduleEnabled.isChecked = enabled
        }
        viewModel.timeWindows.observe(this) { windows ->
            timeWindowAdapter.updateWindows(windows)
            tvNoWindows.visibility = if (windows.isEmpty()) View.VISIBLE else View.GONE
            rvTimeWindows.visibility = if (windows.isEmpty()) View.GONE else View.VISIBLE
        }
        viewModel.isDeviceAdminActive.observe(this) { active ->
            tvDeviceAdminStatus.text = if (active) {
                getString(R.string.device_admin_active)
            } else {
                getString(R.string.device_admin_inactive)
            }
        }
    }

    private fun updateCodeGenerationUI(isPaid: Boolean) {
        if (isPaid) {
            etCodeCount.visibility = View.VISIBLE
            if (etCodeCount.text.isEmpty()) {
                etCodeCount.setText("30")
            }
            btnGenerateCodes.text = getString(R.string.generate_codes_paid)
        } else {
            etCodeCount.visibility = View.GONE
            btnGenerateCodes.text = getString(R.string.generate_codes_free)
        }
    }

    private fun setupClickListeners() {
        btnSetLimit.setOnClickListener {
            val currentMinutes = viewModel.dailyLimitMinutes.value ?: 60
            val currentHours = currentMinutes / 60
            val currentMins = currentMinutes % 60
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    val totalMinutes = hourOfDay * 60 + minute
                    viewModel.setDailyLimitMinutes(totalMinutes)
                },
                currentHours,
                currentMins,
                true
            ).show()
        }

        btnGenerateCodes.setOnClickListener {
            val isPaid = viewModel.isPaidVersion.value ?: false
            val count = if (isPaid) {
                etCodeCount.text.toString().toIntOrNull() ?: 30
            } else {
                3
            }
            val minutes = etMinutesPerCode.text.toString().toIntOrNull() ?: 30
            viewModel.generateCodes(count, minutes)
            if (isPaid) {
                etCodeCount.setText("30")
            }
        }

        btnChangePin.setOnClickListener {
            val newPin = etNewPin.text.toString()
            viewModel.changePin(newPin)
            etNewPin.setText("")
        }

        btnUnlock.setOnClickListener {
            viewModel.toggleUnlockUntilTomorrow()
        }

        btnOpenAccessibilitySettings.setOnClickListener {
            tvAccessibilityInstructions.visibility = View.VISIBLE
            viewModel.openAccessibilitySettings()
        }

        btnOpenUsageStatsSettings.setOnClickListener {
            tvUsageStatsInstructions.visibility = View.VISIBLE
            viewModel.openUsageStatsSettings()
        }

        btnOpenOverlaySettings.setOnClickListener {
            tvOverlayInstructions.visibility = View.VISIBLE
            viewModel.openOverlaySettings()
        }

        switchAutostart.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAutostartEnabled(isChecked)
        }

        switchBlocking.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setBlockingEnabled(isChecked)
        }

        switchTimerEnabled.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setTimerEnabled(isChecked)
        }

        btnExit.setOnClickListener {
            finish()
        }

        // Schedule listener
        switchScheduleEnabled.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setScheduleEnabled(isChecked)
            // Avertissement schedule-only : pas de cap quotidien
            if (isChecked && viewModel.isTimerEnabled.value != true) {
                android.widget.Toast.makeText(this, getString(R.string.schedule_only_warning), android.widget.Toast.LENGTH_LONG).show()
            }
        }

        btnAddWindow.setOnClickListener {
            showAddTimeWindowDialog()
        }

        // Device Admin listener
        btnActivateDeviceAdmin.setOnClickListener {
            viewModel.openDeviceAdminSettings()
        }
    }

    private fun showAddTimeWindowDialog() {
        showTimePickerDialog(
            initialStartHour = 8,
            initialStartMinute = 0,
            initialEndHour = 20,
            initialEndMinute = 0,
            initialType = TimeWindowType.BLOCK,
            initialDaysOfWeek = emptyList(),
            title = getString(R.string.schedule_add_window),
            onConfirm = { startH, startM, endH, endM, type, daysOfWeek ->
                viewModel.addTimeWindow(TimeWindow(
                    startHour = startH,
                    startMinute = startM,
                    endHour = endH,
                    endMinute = endM,
                    type = type,
                    daysOfWeek = daysOfWeek
                ))
            }
        )
    }

    private fun showEditTimeWindowDialog(window: TimeWindow) {
        showTimePickerDialog(
            initialStartHour = window.startHour,
            initialStartMinute = window.startMinute,
            initialEndHour = window.endHour,
            initialEndMinute = window.endMinute,
            initialType = window.type,
            initialDaysOfWeek = window.daysOfWeek,
            title = getString(R.string.schedule_edit_window),
            onConfirm = { startH, startM, endH, endM, type, daysOfWeek ->
                viewModel.updateTimeWindow(window.copy(
                    startHour = startH,
                    startMinute = startM,
                    endHour = endH,
                    endMinute = endM,
                    type = type,
                    daysOfWeek = daysOfWeek
                ))
            }
        )
    }

    private fun showTimePickerDialog(
        initialStartHour: Int,
        initialStartMinute: Int,
        initialEndHour: Int,
        initialEndMinute: Int,
        initialType: TimeWindowType,
        initialDaysOfWeek: List<Int>,
        title: String,
        onConfirm: (startH: Int, startM: Int, endH: Int, endM: Int, type: TimeWindowType, daysOfWeek: List<Int>) -> Unit
    ) {
        var selectedStartHour = initialStartHour
        var selectedStartMinute = initialStartMinute
        var selectedEndHour = initialEndHour
        var selectedEndMinute = initialEndMinute
        var selectedType = initialType
        val selectedDays = if (initialDaysOfWeek.isEmpty()) (1..7).toMutableList() else initialDaysOfWeek.toMutableList()
        val typeOptions = arrayOf(getString(R.string.schedule_type_block), getString(R.string.schedule_type_allow))
        val typeValues = arrayOf(TimeWindowType.BLOCK, TimeWindowType.ALLOW)
        val dayNames = arrayOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
        val dayValues = listOf(1, 2, 3, 4, 5, 6, 7)

        val builder = AlertDialog.Builder(this).setTitle(title)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        fun label(text: String, top: Int = 0) = android.widget.TextView(this).apply {
            this.text = text
            setTextColor(getColor(R.color.white))
            textSize = 16f
            setPadding(0, top, 0, 0)
        }
        layout.addView(label(getString(R.string.schedule_start_time)))
        layout.addView(Button(this).apply {
            text = String.format("%02d:%02d", selectedStartHour, selectedStartMinute)
            setOnClickListener { TimePickerDialog(this@AdminActivity, { _, h, m -> selectedStartHour = h; selectedStartMinute = m; text = String.format("%02d:%02d", h, m) }, selectedStartHour, selectedStartMinute, true).show() }
        })
        layout.addView(label(getString(R.string.schedule_end_time), 24))
        layout.addView(Button(this).apply {
            text = String.format("%02d:%02d", selectedEndHour, selectedEndMinute)
            setOnClickListener { TimePickerDialog(this@AdminActivity, { _, h, m -> selectedEndHour = h; selectedEndMinute = m; text = String.format("%02d:%02d", h, m) }, selectedEndHour, selectedEndMinute, true).show() }
        })
        layout.addView(label(getString(R.string.schedule_days_label), 24))
        val dayCheckBoxes = dayValues.mapIndexed { index, day -> android.widget.CheckBox(this).apply {
            text = dayNames[index]
            setTextColor(getColor(R.color.white))
            isChecked = selectedDays.contains(day)
            setOnCheckedChangeListener { _, checked -> if (checked) { if (!selectedDays.contains(day)) selectedDays.add(day) } else selectedDays.remove(day) }
        }}
        val daysLayout = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(0, 8, 0, 0) }
        dayCheckBoxes.forEach(daysLayout::addView)
        daysLayout.addView(Button(this).apply { text = getString(R.string.schedule_every_day); textSize = 13f; setOnClickListener { selectedDays.clear(); dayCheckBoxes.forEach { it.isChecked = true } } })
        layout.addView(daysLayout)
        layout.addView(label(getString(R.string.schedule_type_label), 24))
        layout.addView(android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@AdminActivity, android.R.layout.simple_spinner_dropdown_item, typeOptions)
            setSelection(typeValues.indexOf(initialType))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) { selectedType = typeValues[pos] }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        })
        builder.setView(android.widget.ScrollView(this).apply { addView(layout) })
        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            if (selectedStartHour == selectedEndHour && selectedStartMinute == selectedEndMinute) {
                Toast.makeText(this, getString(R.string.schedule_invalid_window), Toast.LENGTH_LONG).show()
                return@setPositiveButton
            }
            val finalDays = if (selectedDays.size == 7 || selectedDays.isEmpty()) emptyList() else selectedDays.sorted()
            onConfirm(selectedStartHour, selectedStartMinute, selectedEndHour, selectedEndMinute, selectedType, finalDays)
        }
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.show()
    }

    private fun confirmDeleteTimeWindow(window: TimeWindow) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.schedule_delete_title))
            .setMessage(getString(R.string.schedule_delete_message, window.formatTime()))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.removeTimeWindow(window.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun isSystemPackage(packageName: String): Boolean {
        return packageName.startsWith("com.android") ||
                packageName.startsWith("android") ||
                packageName == "com.google.android.tv.settings" ||
                packageName == "com.google.android.leanbacklauncher"
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadSettings()
        viewModel.checkPermissions()
        viewModel.updateRemainingTime()
        viewModel.loadCodes()
        viewModel.loadScheduleSettings()
    }
}
