package terminwatcher.core.internal

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import terminwatcher.core.api.CheckResult
import terminwatcher.core.api.DateFilter
import terminwatcher.core.api.PageSource
import terminwatcher.core.api.ServiceQuery
import terminwatcher.core.api.Status
import terminwatcher.core.api.TerminClient
import terminwatcher.core.api.freshDates
import kotlin.time.Clock

internal class DefaultTerminClient(
    private val source: PageSource,
    private val clock: Clock,
    private val timeZone: TimeZone,
) : TerminClient {

    override suspend fun check(
        query: ServiceQuery,
        knownDates: Set<String>,
        filter: DateFilter?,
    ): CheckResult {
        val page = try {
            source.fetchCalendar(query)
        } catch (e: Exception) {
            return CheckResult(query = query, status = Status.UNKNOWN, detail = e.toString())
        }
        val result = classifyPage(page.html, page.finalUrl, page.httpStatus)
        val fresh = if (result.status == Status.AVAILABLE) freshDates(knownDates, result.dates) else emptyList()
        val matches = filter
            ?.matches(result.days.filter { it.isoDate in fresh }, today())
            .orEmpty()
        return CheckResult(
            query = query,
            status = result.status,
            dates = result.dates,
            freshDates = fresh,
            days = result.days,
            matches = matches,
            detail = result.detail,
            html = page.html,
            finalUrl = page.finalUrl,
            serviceName = result.serviceName,
        )
    }

    override fun today(): LocalDate = clock.now().toLocalDateTime(timeZone).date
}
