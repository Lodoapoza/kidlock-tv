package uk.telegramgames.lodolock

data class AppSettings(
    val pin: String = "",
    val dailyTimeLimitMinutes: Int = 60
)
