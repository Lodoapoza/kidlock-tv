package uk.telegramgames.lodolock

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import java.util.Calendar

class PinInputFragment : Fragment() {
    private lateinit var viewModel: PinInputViewModel
    private lateinit var etPinInput: EditText
    private lateinit var tvErrorMessage: TextView
    private var isUpdatingPinInput = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_pin_input, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[PinInputViewModel::class.java]

        etPinInput = view.findViewById(R.id.etPinInput)
        tvErrorMessage = view.findViewById(R.id.tvErrorMessage)

        setupPinInput()
        setupObservers()

        etPinInput.post {
            etPinInput.requestFocus()
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(etPinInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun setupPinInput() {
        etPinInput.inputType = InputType.TYPE_CLASS_NUMBER or 
            InputType.TYPE_NUMBER_VARIATION_PASSWORD

        etPinInput.filters = arrayOf(
            InputFilter { source, start, end, dest, dstart, dend ->
                if (source == null || source.isEmpty()) {
                    return@InputFilter null
                }

                val filteredSource = StringBuilder()
                for (i in start until end) {
                    val char = source[i]
                    if (char.isDigit()) {
                        filteredSource.append(char)
                    }
                }

                if (filteredSource.isEmpty()) {
                    return@InputFilter ""
                }

                val destLength = dest?.length ?: 0
                val safeDstart = dstart.coerceIn(0, destLength)
                val safeDend = dend.coerceIn(safeDstart, destLength)
                val replacementLength = safeDend - safeDstart
                val resultLength = destLength - replacementLength + filteredSource.length

                if (resultLength > 6) {
                    val allowedLength = 6 - (destLength - replacementLength)
                    if (allowedLength <= 0) {
                        return@InputFilter ""
                    }
                    return@InputFilter filteredSource.substring(0, allowedLength.coerceAtMost(filteredSource.length))
                }

                filteredSource.toString()
            }
        )

        etPinInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingPinInput) return

                val text = s?.toString() ?: ""

                val currentPin = viewModel.pinInput.value ?: ""
                if (text != currentPin) {
                    viewModel.setPinInput(text)
                }
            }
        })

        // Обработка нажатия Enter/Done на клавиатуре
        etPinInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || 
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val currentText = etPinInput.text?.toString() ?: ""
                viewModel.setPinInput(currentText)
                viewModel.verifyPin()
                true
            } else {
                false
            }
        }
    }

    private fun setupObservers() {
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            tvErrorMessage.text = error ?: ""
            tvErrorMessage.visibility = if (error.isNullOrEmpty()) View.GONE else View.VISIBLE

            if (!error.isNullOrEmpty()) {
                isUpdatingPinInput = true
                etPinInput.setText("")
                viewModel.clearPin()
                isUpdatingPinInput = false
            }
        }

        viewModel.isPinCorrect.observe(viewLifecycleOwner) { isCorrect ->
            if (isCorrect) {
                val repository = DataRepository.getInstance(requireContext())
                val now = Calendar.getInstance()
                val activeWindow = if (repository.isScheduleEnabled()) {
                    ScheduleManager.getActiveBlockWindow(now, repository)
                } else {
                    null
                }

                if (activeWindow != null) {
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.schedule_admin_choice_title)
                        .setMessage(R.string.schedule_admin_choice_message)
                        .setNegativeButton(R.string.schedule_admin_choice_admin) { _, _ ->
                            openAdminWithoutUnlock()
                        }
                        .setPositiveButton(R.string.schedule_admin_choice_unlock) { _, _ ->
                            repository.setScheduleUnlockUntil(
                                ScheduleManager.windowEndMillis(now, activeWindow)
                            )
                            ScreenTimeAccessibilityService.clearBlockNotification(requireContext())
                            // The lock screen is no longer needed; return to
                            // the launcher/previous app while the bypass lasts.
                            requireActivity().finishAffinity()
                        }
                        .setOnCancelListener {
                            viewModel.clearPin()
                        }
                        .show()
                } else {
                    openAdminWithoutUnlock()
                }
            }
        }
    }

    private fun openAdminWithoutUnlock() {
        startActivity(Intent(requireContext(), AdminActivity::class.java))
        requireActivity().finish()
    }
    
    override fun onResume() {
        super.onResume()
        etPinInput.requestFocus()
    }
}
