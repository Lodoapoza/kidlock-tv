package uk.telegramgames.lodolock

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

class OnboardingActivity : AppCompatActivity(), OnboardingNavigator {
    private lateinit var viewPager: ViewPager2
    private val steps = Step.values().toList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.onboardingPager)
        viewPager.adapter = OnboardingPagerAdapter(this)
        viewPager.isUserInputEnabled = false
        // Keep only current page to prevent focus leaking to adjacent pages
        viewPager.offscreenPageLimit = 1

        // Configure ViewPager2's inner RecyclerView for proper D-pad navigation
        viewPager.getChildAt(0)?.let { recyclerView ->
            recyclerView.isFocusable = false
            recyclerView.isFocusableInTouchMode = false
            (recyclerView as? RecyclerView)?.let { rv ->
                rv.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                // Prevent focus from escaping to adjacent pages
                rv.isFocusable = false
            }
        }

        // Transfer focus to fragment content after page change
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                viewPager.post {
                    // Request focus on the current page's first focusable element
                    requestFocusOnCurrentPage()
                }
            }
        })
    }

    /**
     * Request focus on the current ViewPager page.
     * Uses the RecyclerView's current visible view to find and focus on focusable elements.
     */
    fun requestFocusOnCurrentPage() {
        val recyclerView = viewPager.getChildAt(0) as? RecyclerView ?: return
        val currentView = recyclerView.findViewHolderForAdapterPosition(viewPager.currentItem)?.itemView
        (currentView as? ViewGroup)?.let { root ->
            root.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            // Find existing focus or request new focus
            if (root.findFocus() == null) {
                root.requestFocus()
            }
        }
    }

    override fun goNext() {
        var nextIndex = viewPager.currentItem + 1
        // Skip TIME_LIMIT if schedule mode selected
        if (nextIndex < steps.size && steps[nextIndex] == Step.TIME_LIMIT) {
            val viewModel = androidx.lifecycle.ViewModelProvider(this)[OnboardingViewModel::class.java]
            if (viewModel.blockingMode.value == "schedule") {
                nextIndex++
            }
        }
        if (nextIndex < steps.size) {
            viewPager.setCurrentItem(nextIndex, true)
        } else {
            finishOnboarding(openAdmin = true)
        }
    }

    override fun goBack() {
        var prevIndex = viewPager.currentItem - 1
        // Skip TIME_LIMIT if schedule mode selected
        if (prevIndex >= 0 && steps[prevIndex] == Step.TIME_LIMIT) {
            val viewModel = androidx.lifecycle.ViewModelProvider(this)[OnboardingViewModel::class.java]
            if (viewModel.blockingMode.value == "schedule") {
                prevIndex--
            }
        }
        if (prevIndex >= 0) {
            viewPager.setCurrentItem(prevIndex, true)
        }
    }

    override fun finishOnboarding(openAdmin: Boolean) {
        val intent = if (openAdmin) {
            Intent(this, AdminActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        if (viewPager.currentItem > 0) {
            goBack()
        } else {
            super.onBackPressed()
        }
    }

    private enum class Step {
        WELCOME,
        PIN,
        ACCESSIBILITY,
        USAGE_STATS,
        OVERLAY,
        AUTOSTART,
        MODE,
        TIME_LIMIT,
        CODES
    }

    private inner class OnboardingPagerAdapter(activity: AppCompatActivity) :
        FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = steps.size

        override fun createFragment(position: Int): Fragment {
            return when (steps[position]) {
                Step.WELCOME -> OnboardingWelcomeFragment()
                Step.PIN -> OnboardingPinFragment()
                Step.ACCESSIBILITY -> OnboardingAccessibilityFragment()
                Step.USAGE_STATS -> OnboardingUsageStatsFragment()
                Step.OVERLAY -> OnboardingOverlayFragment()
                Step.AUTOSTART -> OnboardingAutostartFragment()
                Step.MODE -> OnboardingModeFragment()
                Step.TIME_LIMIT -> OnboardingTimeLimitFragment()
                Step.CODES -> OnboardingCodesFragment()
            }
        }
    }
}
