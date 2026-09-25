package uk.telegramgames.lodolock

import java.util.Calendar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeWindowTest {
    @Test
    fun overnightWindowOverlapsMorningWindow() {
        val overnight = TimeWindow(startHour = 22, startMinute = 0, endHour = 7, endMinute = 0)
        val morning = TimeWindow(startHour = 6, startMinute = 0, endHour = 8, endMinute = 0)

        assertTrue(overnight.overlaps(morning))
    }

    @Test
    fun earlyMorningWindowOverlapsOvernightWindowAcrossMidnight() {
        val overnight = TimeWindow(startHour = 23, startMinute = 0, endHour = 1, endMinute = 0)
        val earlyMorning = TimeWindow(startHour = 0, startMinute = 30, endHour = 2, endMinute = 0)

        assertTrue(overnight.overlaps(earlyMorning))
    }

    @Test
    fun windowsOnDifferentDaysDoNotOverlap() {
        val monday = TimeWindow(
            startHour = 8,
            startMinute = 0,
            endHour = 10,
            endMinute = 0,
            daysOfWeek = listOf(1)
        )
        val tuesday = TimeWindow(
            startHour = 8,
            startMinute = 0,
            endHour = 10,
            endMinute = 0,
            daysOfWeek = listOf(2)
        )

        assertFalse(monday.overlaps(tuesday))
    }

    @Test
    fun zeroDurationWindowIsNotAValidOverlap() {
        val zeroDuration = TimeWindow(startHour = 10, startMinute = 0, endHour = 10, endMinute = 0)
        val regular = TimeWindow(startHour = 9, startMinute = 0, endHour = 11, endMinute = 0)

        assertFalse(zeroDuration.overlaps(regular))
    }

    @Test
    fun emptyDaysApplyEveryDay() {
        val window = TimeWindow(startHour = 8, startMinute = 0, endHour = 10, endMinute = 0)
        val sunday = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        }

        assertTrue(window.isActiveOnDay(sunday))
    }
}
