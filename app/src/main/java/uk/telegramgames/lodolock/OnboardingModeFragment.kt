package uk.telegramgames.lodolock

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class OnboardingModeFragment : Fragment() {
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding_mode, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnTimer = view.findViewById<Button>(R.id.btnModeTimer)
        val btnSchedule = view.findViewById<Button>(R.id.btnModeSchedule)
        val btnBack = view.findViewById<Button>(R.id.btnModeBack)

        btnTimer.setOnClickListener {
            viewModel.setBlockingMode("timer")
            val limit = DataRepository.getInstance(requireContext()).getDailyTimeLimitMinutes()
            android.widget.Toast.makeText(
                requireContext(),
                getString(R.string.onboarding_timer_hint, TimeManager.formatMinutes(requireContext(), limit)),
                android.widget.Toast.LENGTH_LONG
            ).show()
            (activity as? OnboardingNavigator)?.goNext()
        }

        btnSchedule.setOnClickListener {
            viewModel.setBlockingMode("schedule")
            android.widget.Toast.makeText(
                requireContext(),
                getString(R.string.onboarding_schedule_hint),
                android.widget.Toast.LENGTH_LONG
            ).show()
            (activity as? OnboardingNavigator)?.goNext()
        }

        btnBack.setOnClickListener {
            (activity as? OnboardingNavigator)?.goBack()
        }

        // Défaut sur « Plage horaire » : éviter d'activer le minuteur par accident
        // (un appui OK sur le focus initial ne doit pas déclencher un mode non voulu).
        btnSchedule.requestFocus()
    }
}