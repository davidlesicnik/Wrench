package com.lesicnik.wrench.data.repository

import com.google.gson.Gson
import com.lesicnik.wrench.data.remote.ReminderRecord
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReminderTest {
    @Test
    fun `both schedule retains its date even when mileage causes urgency`() {
        val record = Gson().fromJson("""{
            "id":12,"vehicleId":3,"description":"Oil change","userMetric":"Both",
            "metric":"Odometer","urgency":"VeryUrgent","dueDate":"2026-12-05",
            "dueOdometer":150000,"dueDistance":25,"notes":"Use approved oil","tags":"engine"
        }""", ReminderRecord::class.java).toReminder()
        assertEquals(ReminderMetric.BOTH, record.metric)
        assertEquals(LocalDate.of(2026, 12, 5), record.dueDate)
        assertEquals(150000, record.dueOdometer)
        assertEquals(ReminderUrgency.VERY_URGENT, record.urgency)
    }

    @Test
    fun `mileage reminder ignores the servers placeholder date`() {
        val record = Gson().fromJson("""{
            "Id":"12","VehicleId":"3","Description":"Rotate tyres","UserMetric":"Odometer",
            "Metric":"Odometer","Urgency":"PastDue","DueDate":"0001-01-01",
            "DueOdometer":"10000","DueDistance":"-200"
        }""", ReminderRecord::class.java).toReminder()
        assertNull(record.dueDate)
        assertEquals(-200, record.dueDistance)
        assertEquals("", record.notes)
    }

    @Test
    fun `old responses without user metric fall back to metric`() {
        val record = ReminderRecord(id = "1", vehicleId = "2", metric = "Date", dueDate = "2026-02-28", dueOdometer = "0").toReminder()
        assertEquals(ReminderMetric.DATE, record.metric)
        assertNull(record.dueOdometer)
        assertEquals(ReminderUrgency.UNKNOWN, record.urgency)
    }

    @Test
    fun `invalid schedules and dates fail rather than inventing calendar events`() {
        val base = ReminderRecord(id = "1", vehicleId = "2", metric = "Date", dueDate = "2026-11-05")
        assertThrows(Exception::class.java) { base.copy(dueDate = "not a date").toReminder() }
        assertThrows(Exception::class.java) { base.copy(dueDate = "0001-01-01").toReminder() }
        assertThrows(Exception::class.java) { base.copy(metric = "Unexpected").toReminder() }
        assertThrows(Exception::class.java) { base.copy(id = null).toReminder() }
    }
}
