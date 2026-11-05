package com.lesicnik.wrench.data.repository

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MaintenanceAgendaTest {
    private fun reminder(id: Int, urgency: ReminderUrgency, date: LocalDate? = null, distance: Int? = null) =
        Reminder(id, 1, "Task $id", if (distance == null) ReminderMetric.DATE else if (date == null) ReminderMetric.ODOMETER else ReminderMetric.BOTH,
            urgency, date, distance?.let { 10000 + it }, distance, "", "")

    @Test fun `overdue mileage takes priority over future dated maintenance`() {
        val today = LocalDate.of(2026, 11, 5)
        val sorted = maintenanceAgenda(listOf(
            reminder(1, ReminderUrgency.NOT_URGENT, today.plusDays(3)),
            reminder(2, ReminderUrgency.PAST_DUE, distance = -100),
            reminder(3, ReminderUrgency.VERY_URGENT, distance = 30)
        ))
        assertEquals(listOf(2, 3, 1), sorted.map { it.id })
    }

    @Test fun `combined schedules appear once and ordering is deterministic`() {
        val both = reminder(1, ReminderUrgency.URGENT, LocalDate.of(2026, 11, 6), 100)
        val date = reminder(2, ReminderUrgency.URGENT, LocalDate.of(2026, 11, 7))
        assertEquals(listOf(both, date), maintenanceAgenda(listOf(date, both)))
        assertTrue(maintenanceAgenda(emptyList()).isEmpty())
    }
}
