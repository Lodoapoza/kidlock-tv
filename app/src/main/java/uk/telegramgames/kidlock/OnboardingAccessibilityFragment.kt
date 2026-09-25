package uk.telegramgames.kidlock

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class OnboardingAccessibilityFragment : Fragment() {
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding_accessibility, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvStatus = view.findViewById<TextView>(R.id.tvAccessibilityStatus)
        val btnOpenSettings = view.findViewById<Button>(R.id.btnOpenAccessibilitySettings)
        val btnNext = view.findViewById<Button>(R.id.btnAccessibilityNext)
        val btnBack = view.findViewById<Button>(R.id.btnAccessibilityBack)

        btnOpenSettings.setOnClickListener {
            viewModel.openAccessibilitySettings()
        }

        btnNext.setOnClickListener {
            (activity as? OnboardingNavigator)?.goNext()
        }

        btnBack.setOnClickListener {
            (activity as? OnboardingNavigator)?.goBack()
        }

        viewModel.isAccessibilityServiceEnabled.observe(viewLifecycleOwner) { enabled ->
            val statusText = if (enabled) {
                getString(R.string.accessibility_enabled)
            } else {
                getString(R.string.accessibility_disabled)
            }
            tvStatus.text = getString(R.string.onboarding_accessibility_status_format, statusText)
            tvStatus.setTextColor(if (enabled) requireContext().getColor(R.color.status_good) else requireContext().getColor(R.color.status_bad))
            btnNext.isEnabled = enabled

            // Focus on Next if enabled, otherwise on Open Settings
            if (enabled) {
                btnNext.requestFocus()
            } else {
                btnOpenSettings.requestFocus()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
        // Restore focus after returning from settings
        (activity as? OnboardingActivity)?.requestFocusOnCurrentPage()
    }
}
