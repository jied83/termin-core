package terminwatcher.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import terminwatcher.core.api.FetchedPage
import terminwatcher.core.api.Status
import terminwatcher.core.api.classify
import terminwatcher.core.internal.bookableDays

private const val TAG_URL =
    "https://service.berlin.de/terminvereinbarung/termin/tag.php?termin=1&anliegen[]=120882"
private const val TAKEN_URL = "https://service.berlin.de/terminvereinbarung/termin/taken/"
private const val DAY_URL = "https://service.berlin.de/terminvereinbarung/termin/day/"

private fun fixture(name: String): String =
    ParserFixtureTest::class.java.getResource("/fixtures/$name")!!.readText(Charsets.UTF_8)

class ParserFixtureTest {

    @Test
    fun `blocked page`() {
        assertEquals(Status.BLOCKED, classify(FetchedPage(fixture("blocked.html"), TAG_URL, 403)).status)
    }

    @Test
    fun `blocked page even with 200`() {
        assertEquals(Status.BLOCKED, classify(FetchedPage(fixture("blocked.html"), TAG_URL, 200)).status)
    }

    @Test
    fun `taken redirect means no appointments`() {
        assertEquals(Status.NONE, classify(FetchedPage(fixture("taken.html"), TAKEN_URL, 200)).status)
    }

    @Test
    fun `calendar with bookable days`() {
        val r = classify(FetchedPage(fixture("calendar_available.html"), TAG_URL, 200))
        assertEquals(Status.AVAILABLE, r.status)
        assertEquals(listOf("2026-09-24", "2026-10-02"), r.dates)
    }

    @Test
    fun `calendar without bookable days`() {
        assertEquals(Status.NONE, classify(FetchedPage(fixture("calendar_empty.html"), TAG_URL, 200)).status)
    }

    @Test
    fun `unrecognised page`() {
        val html = "<html><head><title>Wartung</title></head><body>Hallo</body></html>"
        assertEquals(Status.UNKNOWN, classify(FetchedPage(html, TAG_URL, 200)).status)
    }

    @Test
    fun `bookable days expose the slot url`() {
        val days = bookableDays(fixture("calendar_available.html"))
        assertEquals(2, days.size)
        assertEquals("https://service.berlin.de/terminvereinbarung/termin/time/1790200800/", days[0].url)
        assertEquals("", days[1].url)
    }

    @Test
    fun `a real calendar page yields every free day`() {
        val r = classify(FetchedPage(fixture("calendar_live.html"), DAY_URL, 200))
        assertEquals(Status.AVAILABLE, r.status)
        assertEquals(22, r.days.size)
        assertEquals(22, r.dates.size)
        assertEquals("2026-10-01", r.dates.first())
        assertEquals(r.dates.sorted(), r.dates)
        assertTrue(r.days.all { it.date != null }, "every live cell should parse to a date")
    }

    @Test
    fun `a real calendar page names the service`() {
        val r = classify(FetchedPage(fixture("calendar_live.html"), DAY_URL, 200))
        assertEquals("Wohnsitz - Alleinige Wohnung oder Hauptwohnung anmelden", r.serviceName)
    }

    @Test
    fun `pages without a selection card have no service name`() {
        assertNull(classify(FetchedPage(fixture("taken.html"), TAKEN_URL, 200)).serviceName)
        assertNull(classify(FetchedPage(fixture("calendar_available.html"), TAG_URL, 200)).serviceName)
    }

    @Test
    fun `real cells are dated from their aria-label, not the epoch in the href`() {
        val days = bookableDays(fixture("calendar_live.html"))
        val first = days.first()
        assertEquals("2026-10-01", first.isoDate)
        assertTrue(first.url.startsWith("https://service.berlin.de/terminvereinbarung/termin/time/"))
        val fromHref = bookableDays(
            fixture("calendar_live.html").replace(Regex("""\s+aria-label="[^"]*""""), "")
        )
        assertEquals(days.map { it.isoDate }, fromHref.map { it.isoDate })
    }

    @Test
    fun `the 403 page the WAF serves is recognised whatever the status code says`() {
        val r = classify(FetchedPage(fixture("blocked.html"), DAY_URL, null))
        assertEquals(Status.BLOCKED, r.status)
    }
}
