package uk.telegramgames.kidlock

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class OnboardingPinFragment : Fragment() {
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding_pin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etPin = view.findViewById<EditText>(R.id.etPin)
        val etPinConfirm = view.findViewById<EditText>(R.id.etPinConfirm)
        val tvError = view.findViewById<TextView>(R.id.tvPinError)
        val btnNext = view.findViewById<Button>(R.id.btnPinNext)
        val btnBack = view.findViewById<Button>(R.id.btnPinBack)

        val filters = arrayOf<InputFilter>(InputFilter.LengthFilter(6))
        etPin.filters = filters
        etPinConfirm.filters = filters

        btnNext.isEnabled = false

        val pinWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateNextButtonState(etPin, etPinConfirm, btnNext, tvError)
            }
        }

        etPin.addTextChangedListener(pinWatcher)
        etPinConfirm.addTextChangedListener(pinWatcher)

        btnNext.setOnClickListener {
            viewModel.clearPinError()
            val pin = etPin.text.toString()
            val confirm = etPinConfirm.text.toString()

            if (pin != confirm) {
                tvError.text = getString(R.string.onboarding_pin_mismatch)
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            val isValid = viewModel.setAdminPin(pin)
            if (isValid) {
                tvError.visibility = View.GONE
                (activity as? OnboardingNavigator)?.goNext()
            } else {
                tvError.visibility = View.VISIBLE
            }
        }

        btnBack.setOnClickListener {
            (activity as? OnboardingNavigator)?.goBack()
        }

        viewModel.pinErrorMessage.observe(viewLifecycleOwner) { error ->
            tvError.text = error ?: ""
            tvError.visibility = if (error.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        etPin.requestFocus()
    }

    private fun updateNextButtonState(
        etPin: EditText,
        etPinConfirm: EditText,
        btnNext: Button,
        tvError: TextView
    ) {
        val pin = etPin.text.toString()
        val confirm = etPinConfirm.text.toString()

        val isValid = pin.length == 6 && confirm.length == 6 && pin == confirm
        btnNext.isEnabled = isValid

        if (pin.isNotEmpty() && confirm.isNotEmpty() && pin != confirm) {
            tvError.text = getString(R.string.onboarding_pin_mismatch)
            tvError.visibility = View.VISIBLE
        } else if (pin.isEmpty() || confirm.isEmpty()) {
            tvError.visibility = View.GONE
        } else if (pin == confirm) {
            tvError.visibility = View.GONE
        }
    }
}
