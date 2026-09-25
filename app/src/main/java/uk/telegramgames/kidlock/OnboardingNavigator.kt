package uk.telegramgames.kidlock

interface OnboardingNavigator {
    fun goNext()
    fun goBack()
    fun finishOnboarding(openAdmin: Boolean)
}
