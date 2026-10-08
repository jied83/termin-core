package terminwatcher.core.api

data class FetchedPage(
    val html: String,
    val finalUrl: String,
    val httpStatus: Int?,
)

fun interface PageSource {
    suspend fun fetchCalendar(query: ServiceQuery): FetchedPage
}
