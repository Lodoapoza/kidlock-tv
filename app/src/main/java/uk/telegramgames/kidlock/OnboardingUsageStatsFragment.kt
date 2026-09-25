package uk.telegramgames.kidlock

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class OnboardingUsageStatsFragment : Fragment() {
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding_usage_stats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvStatus = view.findViewById<TextView>(R.id.tvUsageStatsStatus)
        val tvWarning = view.findViewById<TextView>(R.id.tvUsageStatsWarning)
        val btnOpenSettings = view.findViewById<Button>(R.id.btnOpenUsageStatsSettings)
        val btnNext = view.findViewById<Button>(R.id.btnUsageStatsNext)
        val btnBack = view.findViewById<Button>(R.id.btnUsageStatsBack)

        btnOpenSettings.setOnClickListener {
            viewModel.openUsageStatsSettings()
        }

        btnNext.setOnClickListener {
            (activity as? OnboardingNavigator)?.goNext()
        }

        btnBack.setOnClickListener {
            (activity as? OnboardingNavigator)?.goBack()
        }

        viewModel.isUsageStatsPermissionGranted.observe(viewLifecycleOwner) { granted ->
            val statusText = if (granted) {
                getString(R.string.usage_stats_granted)
            } else {
                getString(R.string.usage_stats_denied)
            }
            tvStatus.text = getString(R.string.onboarding_usage_stats_status_format, statusText)
            tvStatus.setTextColor(if (granted) requireContext().getColor(R.color.status_good) else requireContext().getColor(R.color.status_bad))

            tvWarning.visibility = if (granted) View.GONE else View.VISIBLE
            btnNext.text = if (granted) {
                getString(R.string.onboarding_next)
            } else {
                getString(R.string.onboarding_skip)
            }
        }

        // btnNext is always enabled (shows "Skip" if permission not granted)
        btnNext.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
        // Restore focus after returning from settings
        (activity as? OnboardingActivity)?.requestFocusOnCurrentPage()
    }
}
