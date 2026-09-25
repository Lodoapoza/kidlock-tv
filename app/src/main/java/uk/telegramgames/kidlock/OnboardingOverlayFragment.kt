package uk.telegramgames.kidlock

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class OnboardingOverlayFragment : Fragment() {
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_onboarding_overlay, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val status = view.findViewById<TextView>(R.id.tvOverlayStatus)
        val warning = view.findViewById<TextView>(R.id.tvOverlayWarning)
        val open = view.findViewById<Button>(R.id.btnOpenOverlaySettings)
        val next = view.findViewById<Button>(R.id.btnOverlayNext)
        val back = view.findViewById<Button>(R.id.btnOverlayBack)

        open.setOnClickListener { viewModel.openOverlaySettings() }
        back.setOnClickListener { (activity as? OnboardingNavigator)?.goBack() }
        next.setOnClickListener { (activity as? OnboardingNavigator)?.goNext() }

        viewModel.isOverlayPermissionGranted.observe(viewLifecycleOwner) { granted ->
            status.text = getString(
                R.string.onboarding_overlay_status_format,
                getString(if (granted) R.string.overlay_granted else R.string.overlay_denied)
            )
            status.setTextColor(requireContext().getColor(if (granted) R.color.status_good else R.color.status_bad))
            warning.visibility = if (granted) View.GONE else View.VISIBLE
            next.isEnabled = granted
            if (granted) next.requestFocus() else open.requestFocus()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
        (activity as? OnboardingActivity)?.requestFocusOnCurrentPage()
    }
}
