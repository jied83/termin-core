package terminwatcher.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import terminwatcher.core.api.BookableDay
import terminwatcher.core.api.DateFilter
import terminwatcher.core.api.PollPolicy
import terminwatcher.core.api.ServiceQuery
import terminwatcher.core.api.freshDates

private fun day(iso: String, url: String = "") = BookableDay(LocalDate.parse(iso), iso, url)

class ServiceQueryTest {

    @Test
    fun `all locations use the short calendar url`() {
        val q = ServiceQuery(serviceId = 120882)
        assertEquals("https://service.berlin.de/dienstleistung/120882/", q.serviceUrl)
        assertEquals("https://service.berlin.de/terminvereinbarung/termin/all/120882/", q.calendarUrl)
        assertEquals("120882:all", q.key)
        assertEquals("Dienstleistung 120882", q.title)
    }

    @Test
    fun `specific locations use tag php`() {
        val q = ServiceQuery(serviceId = 120882, locations = listOf(121364, 121362), name = "Anmeldung")
        assertEquals(
            "https://service.berlin.de/terminvereinbarung/termin/tag.php?" +
                "termin=1&anliegen[]=120882&dienstleister[]=121364&dienstleister[]=121362",
            q.calendarUrl,
        )
        assertEquals("Anmeldung", q.title)
    }

    @Test
    fun `the key ignores the order locations were written in`() {
        val a = ServiceQuery(120882, listOf(121364, 121362))
        val b = ServiceQuery(120882, listOf(121362, 121364))
        assertEquals(a.key, b.key)
    }
}

class DateFilterTest {

    @Test
    fun `keeps dates in range on wanted weekdays`() {
        val filter = DateFilter(
            from = LocalDate(2026, 10, 1),
            to = LocalDate(2026, 10, 31),
            weekdays = setOf(DayOfWeek.FRIDAY),
        )
        val days = listOf(
            day("2026-09-25"),
            day("2026-10-01"),
            day("2026-10-02"),
            day("2026-11-06"),
            BookableDay(null, "24", ""),
        )
        assertEquals(
            listOf(LocalDate(2026, 10, 2)),
            filter.matches(days, today = LocalDate(2026, 9, 1)).map { it.date },
        )
    }

    @Test
    fun `never returns dates in the past even when from is older`() {
        val filter = DateFilter(from = LocalDate(2026, 1, 1))
        assertEquals(emptyList(), filter.matches(listOf(day("2026-10-04")), today = LocalDate(2026, 10, 5)))
    }

    @Test
    fun `ANY accepts every future day`() {
        val days = listOf(day("2026-10-02"), day("2026-12-24"))
        assertEquals(days, DateFilter.ANY.matches(days, today = LocalDate(2026, 9, 1)))
    }

    @Test
    fun `of() builds the same filter from plain strings and weekday numbers`() {
        val spelled = DateFilter(
            from = LocalDate(2026, 10, 1),
            to = LocalDate(2026, 10, 31),
            weekdays = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY),
        )
        assertEquals(spelled, DateFilter.of("2026-10-01", "2026-10-31", setOf(5, 1)))
    }

    @Test
    fun `of() treats null and blank as no limit`() {
        assertEquals(DateFilter.ANY, DateFilter.of(null, "", emptySet()))
    }

    @Test
    fun `results are sorted earliest first`() {
        val days = listOf(day("2026-12-24"), day("2026-10-02"), day("2026-11-01"))
        assertEquals(
            listOf("2026-10-02", "2026-11-01", "2026-12-24"),
            DateFilter.ANY.matches(days, today = LocalDate(2026, 9, 1)).map { it.isoDate },
        )
    }
}

class PollPolicyTest {

    private val steady = PollPolicy(intervalMinutes = 10.0, jitterSeconds = 0.0, maxBackoffMinutes = 60.0)

    @Test
    fun `an interval below the floor is refused`() {
        assertFailsWith<IllegalArgumentException> { PollPolicy(intervalMinutes = 0.5) }
    }

    @Test
    fun `failures double the wait up to the ceiling`() {
        assertEquals(600.0, steady.nextDelaySeconds(0))
        assertEquals(1200.0, steady.nextDelaySeconds(1))
        assertEquals(2400.0, steady.nextDelaySeconds(2))
        assertEquals(3600.0, steady.nextDelaySeconds(10))
    }

    @Test
    fun `jitter stays inside its band and never goes below the portal minimum`() {
        val jittery = PollPolicy(intervalMinutes = 2.0, jitterSeconds = 60.0)
        repeat(200) {
            val delay = jittery.nextDelaySeconds(0, Random(it))
            assertTrue(delay >= PollPolicy.MIN_DELAY_SECONDS, "delay $delay dropped below the floor")
            assertTrue(delay <= 180.0, "delay $delay exceeded interval + jitter")
        }
    }
}

class FreshDatesTest {

    @Test
    fun `only dates the caller had not seen`() {
        assertEquals(
            listOf("2026-10-03"),
            freshDates(known = setOf("2026-10-01", "2026-10-02"), current = listOf("2026-10-02", "2026-10-03")),
        )
    }

    @Test
    fun `a date that comes back is new again`() {
        assertEquals(listOf("2026-10-01"), freshDates(known = emptySet(), current = listOf("2026-10-01")))
    }

    @Test
    fun `nothing new is an empty list, not a null`() {
        assertEquals(emptyList(), freshDates(known = setOf("2026-10-01"), current = listOf("2026-10-01")))
    }
}
