package uk.telegramgames.kidlock

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

enum class TimeWindowType {
    BLOCK,
    ALLOW
}

data class TimeWindow(
    val id: String = UUID.randomUUID().toString(),
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val type: TimeWindowType = TimeWindowType.BLOCK,
    val daysOfWeek: List<Int> = emptyList()
) {
    fun toStartMinutes(): Int = startHour * 60 + startMinute
    fun toEndMinutes(): Int = endHour * 60 + endMinute

    fun isActiveOnDay(calendar: Calendar): Boolean {
        if (daysOfWeek.isEmpty()) return true
        val dow = calendar.get(Calendar.DAY_OF_WEEK)
        val mapped = when (dow) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
        return daysOfWeek.contains(mapped)
    }

    fun formatTime(): String {
        val sh = startHour.toString().padStart(2, '0')
        val sm = startMinute.toString().padStart(2, '0')
        val eh = endHour.toString().padStart(2, '0')
        val em = endMinute.toString().padStart(2, '0')
        return "${sh}h${sm} - ${eh}h${em}"
    }

    fun formatDays(): String {
        if (daysOfWeek.isEmpty()) return "Tous les jours"
        if (daysOfWeek.size == 7) return "Tous les jours"
        val names = mapOf(
            1 to "Lun", 2 to "Mar", 3 to "Mer", 4 to "Jeu",
            5 to "Ven", 6 to "Sam", 7 to "Dim"
        )
        val sorted = daysOfWeek.sorted()
        if (sorted == listOf(1, 2, 3, 4, 5)) return "Semaine"
        if (sorted == listOf(6, 7)) return "Week-end"
        return sorted.mapNotNull { names[it] }.joinToString(", ")
    }

    fun overlaps(other: TimeWindow): Boolean {
        if (daysOfWeek.isNotEmpty() && other.daysOfWeek.isNotEmpty()) {
            if (daysOfWeek.none { it in other.daysOfWeek }) return false
        }

        val s1 = toStartMinutes()
        val e1 = toEndMinutes()
        val s2 = other.toStartMinutes()
        val e2 = other.toEndMinutes()

        if (s1 == e1 || s2 == e2) return false

        val normS1 = s1
        val normE1 = if (e1 <= s1) e1 + 24 * 60 else e1

        // Compare the second interval at its natural position and on both
        // sides of the midnight boundary (e.g. 00:30 belongs to the same
        // overnight interval as 23:00 from the previous day).
        return listOf(-24 * 60, 0, 24 * 60).any { shift ->
            val normS2 = s2 + shift
            val normE2 = (if (e2 <= s2) e2 + 24 * 60 else e2) + shift
            normS1 < normE2 && normS2 < normE1
        }
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("sh", startHour)
            put("sm", startMinute)
            put("eh", endHour)
            put("em", endMinute)
            put("type", type.name)
            val daysArray = JSONArray()
            daysOfWeek.forEach { daysArray.put(it) }
            put("days", daysArray)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): TimeWindow {
            val daysArray = json.optJSONArray("days")
            val days = if (daysArray != null) {
                (0 until daysArray.length()).mapNotNull { daysArray.optInt(it).takeIf { d -> d in 1..7 } }
            } else {
                emptyList()
            }
            return TimeWindow(
                id = json.optString("id", UUID.randomUUID().toString()),
                startHour = json.optInt("sh", 0),
                startMinute = json.optInt("sm", 0),
                endHour = json.optInt("eh", 0),
                endMinute = json.optInt("em", 0),
                type = try {
                    TimeWindowType.valueOf(json.optString("type", "BLOCK"))
                } catch (e: Exception) {
                    TimeWindowType.BLOCK
                },
                daysOfWeek = days
            )
        }

        fun listToJson(windows: List<TimeWindow>): String {
            val array = JSONArray()
            windows.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun listFromJson(json: String): List<TimeWindow> {
            if (json.isEmpty()) return emptyList()
            return try {
                val array = JSONArray(json)
                (0 until array.length()).map { array.getJSONObject(it) }
                    .mapNotNull { try { fromJson(it) } catch (e: Exception) { null } }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
