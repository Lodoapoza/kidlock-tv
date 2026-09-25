package uk.telegramgames.lodolock

interface OnboardingNavigator {
    fun goNext()
    fun goBack()
    fun finishOnboarding(openAdmin: Boolean)
}
