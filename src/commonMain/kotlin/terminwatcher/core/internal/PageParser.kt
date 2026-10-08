package terminwatcher.core.internal

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import terminwatcher.core.api.BASE_URL
import terminwatcher.core.api.BERLIN
import terminwatcher.core.api.BookableDay
import terminwatcher.core.api.PageResult
import terminwatcher.core.api.Status
import kotlin.time.Instant

private val BLOCKED_MARKERS = listOf("unerwünschter zugriff", "forbidden access", "zu viele zugriffe")
private val NO_APPOINTMENT_MARKERS = listOf(
    "keine termine für ihre auswahl verfügbar",
    "leider sind aktuell keine termine",
)

private val GERMAN_MONTHS = mapOf(
    "januar" to 1, "februar" to 2, "märz" to 3, "maerz" to 3, "april" to 4, "mai" to 5, "juni" to 6,
    "juli" to 7, "august" to 8, "september" to 9, "oktober" to 10, "november" to 11, "dezember" to 12,
)

private val DATE_IN_LABEL = Regex("""\b(\d{1,2})\.(\d{1,2})\.(\d{4})\b""")

private val TIMESTAMP_IN_HREF = Regex("""/(\d{9,11})/?([?#]|$)""")
private val DAY_IN_LABEL = Regex("""\b(\d{1,2})\b""")
private val MONTH_AND_YEAR = Regex("""([a-zäöü]+)\s+(\d{4})""")

internal fun bookableDays(html: String): List<BookableDay> = bookableDays(Ksoup.parse(html))

internal fun bookableDays(doc: Document): List<BookableDay> = doc.select("td.buchbar").map { cell ->
    val link = cell.selectFirst("a")
    val href = link?.attr("href").orEmpty()
    val label = cell.text().trim()
    BookableDay(
        date = dateFromAriaLabel(link) ?: dateFromHref(href) ?: dateFromTable(cell, label),
        label = label,
        url = when {
            href.isEmpty() || href.startsWith("#") -> ""
            href.startsWith("http") -> href
            else -> BASE_URL + href
        },
    )
}

private fun dateFromAriaLabel(link: Element?): LocalDate? {
    if (link == null) return null
    val text = listOf(link.attr("aria-label"), link.attr("title")).firstOrNull { it.isNotEmpty() } ?: return null
    val match = DATE_IN_LABEL.find(text) ?: return null
    val (day, month, year) = match.destructured
    return date(year.toInt(), month.toInt(), day.toInt())
}

private fun dateFromHref(href: String): LocalDate? {
    val match = TIMESTAMP_IN_HREF.find(href) ?: return null
    val seconds = match.groupValues[1].toLongOrNull() ?: return null
    return Instant.fromEpochSeconds(seconds).toLocalDateTime(BERLIN).date
}

private fun dateFromTable(cell: Element, label: String): LocalDate? {
    val day = DAY_IN_LABEL.find(label)?.groupValues?.get(1)?.toInt() ?: return null
    val table = cell.closest("table") ?: return null
    val header = table.selectFirst("caption[class~=month], th[class~=month]")
        ?: table.selectFirst("caption")
        ?: table.selectFirst("th")
        ?: return null
    val match = MONTH_AND_YEAR.find(header.text().lowercase()) ?: return null
    val month = GERMAN_MONTHS[match.groupValues[1]] ?: return null
    return date(match.groupValues[2].toInt(), month, day)
}

private fun date(year: Int, month: Int, day: Int): LocalDate? =
    runCatching { LocalDate(year, month, day) }.getOrNull()

internal fun classifyPage(html: String?, finalUrl: String?, httpStatus: Int? = null): PageResult {
    val doc = Ksoup.parse(html.orEmpty())
    val title = doc.title().trim().lowercase()
    val text = doc.text().lowercase()
    val url = finalUrl.orEmpty().lowercase()

    if (BLOCKED_MARKERS.any { it in title } || "/termin/stop" in url || httpStatus == 403 || httpStatus == 429) {
        return PageResult(Status.BLOCKED, detail = "http=$httpStatus title='${title.take(80)}'")
    }
    val head = text.take(5000)
    if (BLOCKED_MARKERS.any { it in title || it in head }) {
        return PageResult(Status.BLOCKED, detail = "blocked marker in page, http=$httpStatus")
    }

    val days = bookableDays(doc)
    val dates = days.map { it.isoDate ?: it.label }.filter { it.isNotEmpty() }.distinct().sorted()
    if (dates.isNotEmpty()) return PageResult(Status.AVAILABLE, dates = dates, days = days)

    if ("/termin/taken" in url || NO_APPOINTMENT_MARKERS.any { it in text }) return PageResult(Status.NONE)
    if (doc.select("td.nichtbuchbar, div.calendar-month-table, table.calendar").isNotEmpty()) {
        return PageResult(Status.NONE, detail = "calendar without bookable days")
    }

    return PageResult(Status.UNKNOWN, detail = "http=$httpStatus url=$finalUrl title='${title.take(80)}'")
}
