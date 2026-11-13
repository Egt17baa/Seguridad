package app.seguridad.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 2.0, task 8: the calendar of the automatic copies ([due]): calendar days in the phone's time zone, a clock set back
 * never repeats a copy, and a clock that ran ahead neither loses the schedule nor makes more than one copy.
 */
class BackupScheduleTest {
    private val zone = ZoneId.of("Europe/Madrid")

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun target(schedule: BackupSchedule, lastAt: Long?) = BackupTargetSettings("content://a", 5, schedule, lastAt = lastAt)

    @Test fun dailyWeeklyMonthly() {
        // With no copy yet, every calendar is due; «con cada cambio» never is by the calendar.
        for (s in listOf(BackupSchedule.DAILY, BackupSchedule.WEEKLY, BackupSchedule.MONTHLY)) assertTrue(s.name, target(s, null).due(at(2026, 11, 13), zone))
        assertFalse(target(BackupSchedule.ON_CHANGE, null).due(at(2026, 11, 13), zone))
        assertFalse(target(BackupSchedule.ON_CHANGE, at(2020, 1, 1)).due(at(2026, 11, 13), zone))

        // Daily: calendar days, not 24 hours: 23:50 then 00:10 the next day is due; 00:10 then 23:50 the same day is not.
        val daily = target(BackupSchedule.DAILY, at(2026, 11, 13, 23, 50))
        assertFalse(daily.due(at(2026, 11, 13, 23, 59), zone))
        assertTrue(daily.due(at(2026, 11, 14, 0, 10), zone))
        assertFalse(target(BackupSchedule.DAILY, at(2026, 11, 13, 0, 10)).due(at(2026, 11, 13, 23, 50), zone))
        assertTrue(daily.due(at(2026, 12, 25), zone)) // Days later (the app was not opened): one copy is due.

        // Weekly: the same weekday a week later, or after.
        val weekly = target(BackupSchedule.WEEKLY, at(2026, 11, 13, 20))
        assertFalse(weekly.due(at(2026, 11, 19, 23, 59), zone))
        assertTrue(weekly.due(at(2026, 11, 20, 8), zone))
        assertTrue(weekly.due(at(2026, 12, 1), zone))

        // Monthly: the same day of the next month.
        val monthly = target(BackupSchedule.MONTHLY, at(2026, 11, 13, 20))
        assertFalse(monthly.due(at(2026, 12, 12, 23, 59), zone))
        assertTrue(monthly.due(at(2026, 12, 13, 0, 1), zone))

        // In the phone's zone: 23:30 in Madrid is already the next day in UTC, but not here.
        val late = target(BackupSchedule.DAILY, at(2026, 11, 13, 1))
        assertFalse(late.due(at(2026, 11, 13, 23, 30), zone))
        assertTrue(late.due(at(2026, 11, 13, 23, 30), ZoneId.of("Asia/Tokyo")))
    }

    @Test fun monthlyOnShortMonth() {
        // 31 January → 28 February (2027) or 29 February (2028), the last day of the shorter month.
        val jan31 = target(BackupSchedule.MONTHLY, at(2027, 1, 31))
        assertFalse(jan31.due(at(2027, 2, 27, 23, 59), zone))
        assertTrue(jan31.due(at(2027, 2, 28, 0, 1), zone))
        val leap = target(BackupSchedule.MONTHLY, at(2028, 1, 31))
        assertFalse(leap.due(at(2028, 2, 28, 23, 59), zone))
        assertTrue(leap.due(at(2028, 2, 29, 0, 1), zone))
        // 31 March → 30 April.
        val mar31 = target(BackupSchedule.MONTHLY, at(2027, 3, 31))
        assertFalse(mar31.due(at(2027, 4, 29, 23, 59), zone))
        assertTrue(mar31.due(at(2027, 4, 30, 0, 1), zone))
    }

    @Test fun clockBackDoesNotRepeat() {
        val copied = at(2026, 11, 13, 12)
        for (s in listOf(BackupSchedule.DAILY, BackupSchedule.WEEKLY, BackupSchedule.MONTHLY)) {
            val t = target(s, copied)
            // The clock set back an hour, a day, to the day before midnight: no new copy.
            assertFalse(s.name, t.due(copied - 60 * 60 * 1000L, zone))
            assertFalse(s.name, t.due(at(2026, 11, 12, 23, 59), zone))
            assertFalse(s.name, t.due(at(2026, 11, 13, 0, 1), zone))
            // Opening the app again and again the same day makes none either.
            repeat(5) { assertFalse(s.name, t.due(copied + it * 60_000L, zone)) }
        }
        // A clock that ran ahead (set to 2030, a copy written, then corrected): the schedule is not lost for years. Exactly
        // one copy is due, and the next one follows the real calendar from it.
        val ahead = target(BackupSchedule.WEEKLY, at(2030, 1, 1))
        val real = at(2026, 11, 13)
        assertTrue(ahead.due(real, zone))
        val healed = ahead.copy(lastAt = real)
        assertFalse(healed.due(real + 60_000L, zone))
        assertFalse(healed.due(at(2026, 11, 19), zone))
        assertTrue(healed.due(at(2026, 11, 20), zone))
        // Set back by less than one period (6 days for a weekly one): no copy, the next one waits for its day.
        val weekly = target(BackupSchedule.WEEKLY, at(2026, 11, 13))
        assertFalse(weekly.due(at(2026, 11, 7, 13), zone))
        assertEquals(false, target(BackupSchedule.DAILY, at(2026, 11, 13, 12)).due(at(2026, 11, 12, 13), zone))
    }

    /** Review M-8 (f): the days of the change of time in Madrid (28-03-2027 and 31-10-2027) are calendar days as any other. */
    @Test fun daysOfTheChangeOfTime() {
        // 02:30 does not exist on 28 March (it is 03:30); a day before, at 02:30, is the day before.
        val beforeSpring = target(BackupSchedule.DAILY, at(2027, 3, 27, 2, 30))
        assertTrue(beforeSpring.due(at(2027, 3, 28, 2, 30), zone))
        val springDay = target(BackupSchedule.DAILY, at(2027, 3, 28, 0, 10))
        assertFalse(springDay.due(at(2027, 3, 28, 23, 59), zone))
        assertTrue(springDay.due(at(2027, 3, 29, 0, 1), zone))
        assertTrue(target(BackupSchedule.WEEKLY, at(2027, 3, 25, 2, 30)).due(at(2027, 4, 1, 2, 30), zone))
        assertFalse(target(BackupSchedule.WEEKLY, at(2027, 3, 25, 2, 30)).due(at(2027, 3, 31, 23, 59), zone))
        // 02:30 happens twice on 31 October: the second one, an hour later, is the same day.
        val first = at(2027, 10, 31, 2, 30)
        val autumn = target(BackupSchedule.DAILY, first)
        assertFalse(autumn.due(first + 60 * 60 * 1000L, zone))
        assertFalse(autumn.due(at(2027, 10, 31, 23, 59), zone))
        assertTrue(autumn.due(at(2027, 11, 1, 0, 1), zone))
    }
}
