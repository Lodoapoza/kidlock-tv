package uk.telegramgames.kidlock

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class OnboardingAutostartFragment : Fragment() {
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding_autostart, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnEnable = view.findViewById<Button>(R.id.btnAutostartEnable)
        val btnSkip = view.findViewById<Button>(R.id.btnAutostartSkip)
        val btnBack = view.findViewById<Button>(R.id.btnAutostartBack)

        btnEnable.setOnClickListener {
            viewModel.setAutostartEnabled(true)
            (activity as? OnboardingNavigator)?.goNext()
        }

        btnSkip.setOnClickListener {
            (activity as? OnboardingNavigator)?.goNext()
        }

        btnBack.setOnClickListener {
            (activity as? OnboardingNavigator)?.goBack()
        }

        btnEnable.requestFocus()
    }
}