package uk.telegramgames.kidlock

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class OnboardingCodesFragment : Fragment() {
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding_codes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvCodes = view.findViewById<TextView>(R.id.tvCodesList)
        val btnGenerate = view.findViewById<Button>(R.id.btnGenerateInitialCodes)
        val btnFinish = view.findViewById<Button>(R.id.btnFinishOnboarding)
        val btnOpenAdmin = view.findViewById<Button>(R.id.btnOpenAdminPanel)

        btnGenerate.setOnClickListener {
            viewModel.generateInitialCodes()
        }

        btnFinish.setOnClickListener {
            (activity as? OnboardingNavigator)?.finishOnboarding(openAdmin = false)
        }

        btnOpenAdmin.setOnClickListener {
            (activity as? OnboardingNavigator)?.finishOnboarding(openAdmin = true)
        }

        viewModel.generatedCodes.observe(viewLifecycleOwner) { codes ->
            if (codes.isEmpty()) {
                tvCodes.text = getString(R.string.onboarding_codes_empty)
            } else {
                tvCodes.text = codes.joinToString("\n") { code ->
                    getString(R.string.onboarding_code_format, code.value, code.addedTimeMinutes)
                }
            }
        }

        // Focus on Finish as the primary action to complete onboarding
        btnFinish.requestFocus()
    }
}
