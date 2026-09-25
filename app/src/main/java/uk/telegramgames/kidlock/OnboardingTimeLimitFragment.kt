package uk.telegramgames.kidlock

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class OnboardingTimeLimitFragment : Fragment() {
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding_time_limit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvSelectedLimit = view.findViewById<TextView>(R.id.tvSelectedLimit)
        val btnNext = view.findViewById<Button>(R.id.btnTimeLimitNext)
        val btnBack = view.findViewById<Button>(R.id.btnTimeLimitBack)

        val presetContainer = view.findViewById<LinearLayout>(R.id.layoutPresetContainer)
        val presetMinutes = resources.getIntArray(R.array.onboarding_preset_minutes).toList()

        val presetButtons = mutableListOf<Button>()
        val presetButtonsByMinutes = mutableMapOf<Int, Button>()

        fun dpToPx(dp: Int): Int {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp.toFloat(),
                resources.displayMetrics
            ).toInt()
        }

        fun selectPreset(button: Button, minutes: Int) {
            presetButtons.forEach { it.isSelected = it == button }
            viewModel.setDailyLimitMinutes(minutes)
        }

        // Create preset buttons FIRST (before observer setup)
        presetMinutes.forEachIndexed { index, minutes ->
            val button = Button(requireContext()).apply {
                id = View.generateViewId()
                background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_preset_button)
                ViewCompat.setBackgroundTintList(this, null)
                stateListAnimator = null
                setPadding(dpToPx(20), dpToPx(10), dpToPx(20), dpToPx(10))
                setTextColor(requireContext().getColor(R.color.white))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                text = TimeManager.formatMinutes(requireContext(), minutes)
                setOnClickListener { selectPreset(this, minutes) }
                isFocusable = true
                isFocusableInTouchMode = false
                isClickable = true
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) {
                params.marginStart = dpToPx(12)
            }
            button.layoutParams = params

            presetButtons.add(button)
            presetButtonsByMinutes[minutes] = button
            presetContainer.addView(button)
        }

        // Setup D-pad navigation between preset buttons
        presetButtons.forEachIndexed { index, button ->
            button.nextFocusLeftId = if (index > 0) {
                presetButtons[index - 1].id
            } else {
                button.id
            }
            button.nextFocusRightId = if (index < presetButtons.size - 1) {
                presetButtons[index + 1].id
            } else {
                button.id
            }
            button.nextFocusDownId = btnBack.id
            button.nextFocusUpId = button.id
        }

        // Setup Back/Next buttons navigation
        presetButtons.firstOrNull()?.let { firstPreset ->
            btnBack.nextFocusUpId = firstPreset.id
            btnNext.nextFocusUpId = firstPreset.id
        }

        // Set default value if not selected yet
        if (viewModel.selectedDailyLimitMinutes.value == null) {
            viewModel.setDailyLimitMinutes(30)
        }

        // Setup observer AFTER buttons are created
        viewModel.selectedDailyLimitMinutes.observe(viewLifecycleOwner) { minutes ->
            if (minutes != null && minutes > 0) {
                tvSelectedLimit.text = getString(R.string.onboarding_time_limit_selected_format, minutes)
                btnNext.isEnabled = true
            } else {
                tvSelectedLimit.text = getString(R.string.onboarding_time_limit_selected_placeholder)
                btnNext.isEnabled = false
            }

            // Update selection state
            presetButtons.forEach { it.isSelected = false }
            presetButtonsByMinutes[minutes]?.isSelected = true
        }

        btnNext.setOnClickListener {
            (activity as? OnboardingNavigator)?.goNext()
        }

        btnBack.setOnClickListener {
            (activity as? OnboardingNavigator)?.goBack()
        }

        // Set initial focus - btnNext is enabled because we set default 30 minutes above
        btnNext.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        // Restore focus on Next button when returning to this screen
        view?.findViewById<Button>(R.id.btnTimeLimitNext)?.let { btnNext ->
            if (btnNext.isEnabled) {
                btnNext.requestFocus()
            }
        }
    }
}
