package terminwatcher.core.internal

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import kotlinx.coroutines.delay
import terminwatcher.core.api.BASE_URL
import terminwatcher.core.api.FetchedPage
import terminwatcher.core.api.PageSource
import terminwatcher.core.api.ServiceQuery
import kotlin.time.Duration

private const val RESTRICTION_PATH = "/terminvereinbarung/termin/time/restriction/"

internal class HttpPageSource(
    private val engine: HttpClientEngineFactory<*>,
    private val dwell: Duration,
    private val requestTimeout: Duration,
    private val userAgent: String,
) : PageSource {

    override suspend fun fetchCalendar(query: ServiceQuery): FetchedPage = newClient().use { client ->
        client.get(query.serviceUrl) { browserHeaders(referrer = null) }

        delay(dwell)

        var response = client.get(query.calendarUrl) { browserHeaders(referrer = query.serviceUrl) }
        var html = response.bodyAsText()

        if (RESTRICTION_PATH in response.finalUrl() || "class=\"timeselector" in html) {
            val restrictionUrl = BASE_URL + RESTRICTION_PATH
            response = client.submitForm(
                url = restrictionUrl,
                formParameters = Parameters.build { append("timerestriction", "") },
            ) {
                browserHeaders(referrer = restrictionUrl)
                header("Origin", BASE_URL)
            }
            response = client.followToCalendar(response, referrer = restrictionUrl)
            html = response.bodyAsText()
        }

        FetchedPage(html = html, finalUrl = response.finalUrl(), httpStatus = response.status.value)
    }

    private suspend fun HttpClient.followToCalendar(response: HttpResponse, referrer: String): HttpResponse {
        if (response.status.value !in 300..399) return response
        val location = response.headers[HttpHeaders.Location] ?: return response
        val target = if (location.startsWith("http")) location else BASE_URL + location
        return get(target) { browserHeaders(referrer = referrer) }
    }

    private fun newClient() = HttpClient(engine) {
        expectSuccess = false
        followRedirects = true
        install(HttpCookies)
        install(HttpTimeout) {
            requestTimeoutMillis = requestTimeout.inWholeMilliseconds
            connectTimeoutMillis = 15_000
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.browserHeaders(referrer: String?) {
        header("User-Agent", userAgent)
        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        header("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")
        header("sec-ch-ua", "\"Chromium\";v=\"141\", \"Not?A_Brand\";v=\"24\"")
        header("sec-ch-ua-mobile", "?0")
        header("sec-ch-ua-platform", "\"Linux\"")
        header("Upgrade-Insecure-Requests", "1")
        header("Sec-Fetch-Dest", "document")
        header("Sec-Fetch-Mode", "navigate")
        header("Sec-Fetch-Site", if (referrer == null) "none" else "same-origin")
        header("Sec-Fetch-User", "?1")
        if (referrer != null) header("Referer", referrer)
    }
}

private fun HttpResponse.finalUrl(): String = call.request.url.toString()
