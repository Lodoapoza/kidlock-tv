package uk.telegramgames.kidlock

data class AppSettings(
    val pin: String = "",
    val dailyTimeLimitMinutes: Int = 60
)
