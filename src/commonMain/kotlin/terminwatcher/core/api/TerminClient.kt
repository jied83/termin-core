package terminwatcher.core.api

import kotlinx.datetime.LocalDate

interface TerminClient {

    suspend fun check(query: ServiceQuery, knownDates: Set<String>, filter: DateFilter?): CheckResult

    suspend fun check(query: ServiceQuery): CheckResult = check(query, emptySet(), null)

    fun today(): LocalDate
}
