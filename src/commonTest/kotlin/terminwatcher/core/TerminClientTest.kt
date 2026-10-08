package terminwatcher.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import terminwatcher.core.api.DateFilter
import terminwatcher.core.api.FetchedPage
import terminwatcher.core.api.PageSource
import terminwatcher.core.api.ServiceQuery
import terminwatcher.core.api.Status
import terminwatcher.core.api.TerminCore
import terminwatcher.core.api.freshDates

private const val CALENDAR = """
<html><head><title>Terminvergabe - Auswahl des Tages</title></head><body>
<table><tbody><tr>
  <td class="nichtbuchbar">30</td>
  <td class="buchbar"><a href="/terminvereinbarung/termin/time/1791583200/"
      aria-label="10.10.2026 - An diesem Tag einen Termin buchen">10</a></td>
  <td class="buchbar"><a href="/terminvereinbarung/termin/time/1791669600/"
      aria-label="11.10.2026 - An diesem Tag einen Termin buchen">11</a></td>
</tr></tbody></table></body></html>
"""

private val QUERY = ServiceQuery(120882, name = "Anmeldung")

private fun clockAt(iso: String) = object : Clock {
    override fun now(): Instant = LocalDate.parse(iso).atStartOfDayIn(TimeZone.UTC)
}

private fun clientOn(html: String, url: String = "https://service.berlin.de/terminvereinbarung/termin/day/") =
    TerminCore.client(
        source = { FetchedPage(html, url, 200) },
        clock = clockAt("2026-10-01"),
    )

class TerminClientTest {

    @Test
    fun `reports every free date, and which of them are new`() = runTest {
        val result = clientOn(CALENDAR).check(QUERY, knownDates = setOf("2026-10-10"), filter = null)

        assertEquals(Status.AVAILABLE, result.status)
        assertEquals(listOf("2026-10-10", "2026-10-11"), result.dates)
        assertEquals(listOf("2026-10-11"), result.freshDates)
        assertTrue(result.hasAppointments)
        assertTrue(result.hasNewAppointments)
    }

    @Test
    fun `with nothing known, every date is new`() = runTest {
        val result = clientOn(CALENDAR).check(QUERY)
        assertEquals(listOf("2026-10-10", "2026-10-11"), result.freshDates)
    }

    @Test
    fun `with everything known, nothing is new but the dates are still reported`() = runTest {
        val result = clientOn(CALENDAR).check(QUERY, setOf("2026-10-10", "2026-10-11"), null)
        assertEquals(listOf("2026-10-10", "2026-10-11"), result.dates)
        assertEquals(emptyList(), result.freshDates)
        assertFalse(result.hasNewAppointments)
    }

    @Test
    fun `matches are the days that are both new and wanted`() = runTest {
        val filter = DateFilter(from = LocalDate(2026, 10, 1))
        val result = clientOn(CALENDAR).check(QUERY, setOf("2026-10-10"), filter)
        assertEquals(listOf("2026-10-11"), result.matches.map { it.isoDate })
    }

    @Test
    fun `no filter means no matches, even when dates are free`() = runTest {
        val result = clientOn(CALENDAR).check(QUERY, emptySet(), null)
        assertTrue(result.matches.isEmpty())
        assertTrue(result.freshDates.isNotEmpty())
    }

    @Test
    fun `a blocked portal is reported, not thrown`() = runTest {
        val blocked = "<html><head><title>Unerwünschter Zugriff / Forbidden access</title></head><body></body></html>"
        val result = TerminCore.client(PageSource { FetchedPage(blocked, QUERY.calendarUrl, 403) }).check(QUERY)
        assertEquals(Status.BLOCKED, result.status)
        assertTrue(result.freshDates.isEmpty())
    }

    @Test
    fun `a dead network is an UNKNOWN result, not a crash`() = runTest {
        val client = TerminCore.client(PageSource { throw IllegalStateException("connection reset") })
        val result = client.check(QUERY)
        assertEquals(Status.UNKNOWN, result.status)
        assertTrue("connection reset" in result.detail)
    }

    @Test
    fun `the raw page comes back so a caller can snapshot what it did not recognise`() = runTest {
        val result = clientOn("<html><head><title>Wartung</title></head><body>Hallo</body></html>").check(QUERY)
        assertEquals(Status.UNKNOWN, result.status)
        assertTrue("Wartung" in result.html)
    }
}
