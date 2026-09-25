package uk.telegramgames.kidlock

import java.util.Date

data class Code(
    val value: String,
    val isUsed: Boolean = false,
    val usedDate: Date? = null,
    val addedTimeMinutes: Int = 0
) {
    fun toJsonString(): String {
        return "$value|$isUsed|${usedDate?.time ?: -1}|$addedTimeMinutes"
    }

    companion object {
        fun fromJsonString(json: String): Code? {
            return try {
                val parts = json.split("|")
                if (parts.size == 4) {
                    Code(
                        value = parts[0],
                        isUsed = parts[1].toBoolean(),
                        usedDate = if (parts[2].toLong() > 0) Date(parts[2].toLong()) else null,
                        addedTimeMinutes = parts[3].toInt()
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

