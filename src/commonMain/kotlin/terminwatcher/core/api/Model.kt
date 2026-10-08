package terminwatcher.core.api

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

const val BASE_URL: String = "https://service.berlin.de"

const val BERLIN_TZ: String = "Europe/Berlin"

val BERLIN: TimeZone = TimeZone.of(BERLIN_TZ)

data class ServiceQuery(
    val serviceId: Int,
    val locations: List<Int> = emptyList(),
    val name: String? = null,
) {
    val key: String
        get() = "$serviceId:" + locations.sorted().joinToString(",").ifEmpty { "all" }

    val title: String
        get() = name ?: "Dienstleistung $serviceId"

    val serviceUrl: String
        get() = "$BASE_URL/dienstleistung/$serviceId/"

    val calendarUrl: String
        get() {
            if (locations.isEmpty()) return "$BASE_URL/terminvereinbarung/termin/all/$serviceId/"
            val params = listOf("termin=1", "anliegen[]=$serviceId") + locations.map { "dienstleister[]=$it" }
            return "$BASE_URL/terminvereinbarung/termin/tag.php?" + params.joinToString("&")
        }
}

enum class Status { AVAILABLE, NONE, BLOCKED, UNKNOWN }

data class BookableDay(
    val date: LocalDate?,
    val label: String,
    val url: String,
) {
    val isoDate: String? get() = date?.toString()
}

data class PageResult(
    val status: Status,
    val dates: List<String> = emptyList(),
    val detail: String = "",
    val days: List<BookableDay> = emptyList(),
)

data class CheckResult(
    val query: ServiceQuery,
    val status: Status,
    val dates: List<String> = emptyList(),
    val freshDates: List<String> = emptyList(),
    val days: List<BookableDay> = emptyList(),
    val matches: List<BookableDay> = emptyList(),
    val detail: String = "",
    val html: String = "",
    val finalUrl: String = "",
) {
    val hasAppointments: Boolean get() = status == Status.AVAILABLE
    val hasNewAppointments: Boolean get() = freshDates.isNotEmpty()
}

data class DateFilter(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val weekdays: Set<DayOfWeek> = emptySet(),
) {
    fun accepts(date: LocalDate, today: LocalDate): Boolean {
        if (date < maxOf(from ?: today, today)) return false
        if (to != null && date > to) return false
        return weekdays.isEmpty() || date.dayOfWeek in weekdays
    }

    fun matches(days: List<BookableDay>, today: LocalDate): List<BookableDay> =
        days.filter { it.date != null && accepts(it.date, today) }.sortedBy { it.date }

    companion object {
        val ANY: DateFilter = DateFilter()

        fun of(from: String?, to: String?, weekdays: Set<Int>): DateFilter = DateFilter(
            from = from?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
            to = to?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
            weekdays = weekdays.map { DayOfWeek(it) }.toSet(),
        )
    }
}
