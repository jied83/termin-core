package terminwatcher.core

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import terminwatcher.core.api.TerminCore
import terminwatcher.core.api.ServiceQuery
import terminwatcher.core.api.Status
import terminwatcher.core.api.classify

private const val RESTRICTION_FORM = """
<html><head><title>Terminvergabe - Zeiteinschränkung</title></head><body>
<form class="timeselector form--base" action="/terminvereinbarung/termin/time/restriction/" method="POST">
  <input type="hidden" name="timerestriction" value=""/>
  <button type="submit" class="button submit">Weiter</button>
</form></body></html>
"""

private const val DAY_PAGE = """
<html><head><title>Terminvergabe - Auswahl des Tages</title></head><body>
<table><tbody><tr><td class="buchbar"><a href="/terminvereinbarung/termin/time/1791583200/"
  aria-label="10.10.2026 - An diesem Tag einen Termin buchen">10</a></td></tr></tbody></table>
</body></html>
"""

private fun factoryOf(engine: HttpClientEngine) = object : HttpClientEngineFactory<HttpClientEngineConfig> {
    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine = engine
}

private fun sourceOver(
    seen: MutableList<HttpRequestData>,
    handler: (HttpRequestData) -> Pair<HttpStatusCode, String>,
) = sourceOverResponses(seen) { request ->
    val (status, body) = handler(request)
    Triple(status, body, Headers.Empty)
}

private fun sourceOverResponses(
    seen: MutableList<HttpRequestData>,
    handler: (HttpRequestData) -> Triple<HttpStatusCode, String, Headers>,
) = TerminCore.pageSource(
    engine = factoryOf(
        MockEngine { request ->
            seen += request
            val (status, body, extra) = handler(request)
            val headers = HeadersBuilder().apply {
                append(HttpHeaders.ContentType, "text/html; charset=UTF-8")
                appendAll(extra)
            }.build()
            respond(body, status, headers)
        }
    ),
    dwell = Duration.ZERO,
)

class HttpPageSourceTest {

    @Test
    fun `walks service page, calendar, then submits the time filter`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val source = sourceOver(seen) { request ->
            when {
                "/dienstleistung/" in request.url.encodedPath -> HttpStatusCode.OK to "<html>service</html>"
                request.method == HttpMethod.Post -> HttpStatusCode.OK to DAY_PAGE
                else -> HttpStatusCode.OK to RESTRICTION_FORM
            }
        }

        val page = source.fetchCalendar(ServiceQuery(120882))

        assertEquals(3, seen.size)
        assertEquals("/dienstleistung/120882/", seen[0].url.encodedPath)
        assertEquals("/terminvereinbarung/termin/all/120882/", seen[1].url.encodedPath)
        assertEquals(HttpMethod.Post, seen[2].method)
        assertEquals("/terminvereinbarung/termin/time/restriction/", seen[2].url.encodedPath)
        assertEquals(Status.AVAILABLE, classify(page).status)
    }

    @Test
    fun `skips the time filter when the calendar comes back directly`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val source = sourceOver(seen) { request ->
            if ("/dienstleistung/" in request.url.encodedPath) HttpStatusCode.OK to "<html>service</html>"
            else HttpStatusCode.OK to DAY_PAGE
        }

        source.fetchCalendar(ServiceQuery(120882))

        assertEquals(2, seen.size)
    }

    @Test
    fun `the service page is fetched first, because it sets the delay token`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        sourceOver(seen) { HttpStatusCode.OK to DAY_PAGE }.fetchCalendar(ServiceQuery(120882))

        assertTrue("/dienstleistung/" in seen.first().url.encodedPath)
        assertEquals(
            "https://service.berlin.de/dienstleistung/120882/",
            seen[1].headers[HttpHeaders.Referrer],
        )
    }

    @Test
    fun `every request carries the browser headers the WAF insists on`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        sourceOver(seen) { HttpStatusCode.OK to DAY_PAGE }.fetchCalendar(ServiceQuery(120882))

        for (request in seen) {
            assertTrue(request.headers[HttpHeaders.UserAgent]!!.startsWith("Mozilla/5.0"))
            assertEquals("de-DE,de;q=0.9,en;q=0.8", request.headers[HttpHeaders.AcceptLanguage])
            assertEquals("document", request.headers["Sec-Fetch-Dest"])
            assertEquals("1", request.headers["Upgrade-Insecure-Requests"])
            assertTrue(request.headers["sec-ch-ua"]!!.isNotEmpty())
        }
    }

    @Test
    fun `the 302 answering the form POST is followed as a GET, the way a browser does`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val dayUrl = "/terminvereinbarung/termin/day/"
        val source = sourceOverResponses(seen) { request ->
            when {
                "/dienstleistung/" in request.url.encodedPath ->
                    Triple(HttpStatusCode.OK, "<html>service</html>", Headers.Empty)
                request.method == HttpMethod.Post ->
                    Triple(HttpStatusCode.Found, "", headersOf(HttpHeaders.Location, dayUrl))
                dayUrl in request.url.encodedPath ->
                    Triple(HttpStatusCode.OK, DAY_PAGE, Headers.Empty)
                else -> Triple(HttpStatusCode.OK, RESTRICTION_FORM, Headers.Empty)
            }
        }

        val page = source.fetchCalendar(ServiceQuery(120882))

        assertEquals(4, seen.size)
        assertEquals(HttpMethod.Get, seen[3].method)
        assertEquals(dayUrl, seen[3].url.encodedPath)
        assertEquals(Status.AVAILABLE, classify(page).status)
        assertEquals(200, page.httpStatus)
    }

    @Test
    fun `a 429 burst limit is reported as blocked, so the caller backs off`() = runTest {
        val body = "<html><head><title>Zu viele Anfragen / Too many requests</title></head><body></body></html>"
        val source = sourceOver(mutableListOf()) { request ->
            if ("/dienstleistung/" in request.url.encodedPath) HttpStatusCode.OK to "<html>service</html>"
            else HttpStatusCode.TooManyRequests to body
        }

        val page = source.fetchCalendar(ServiceQuery(120882))

        assertEquals(Status.BLOCKED, classify(page).status)
    }

    @Test
    fun `a 403 comes back as a page to classify, not an exception`() = runTest {
        val blocked = "<html><head><title>Unerwünschter Zugriff / Forbidden access</title></head><body></body></html>"
        val source = sourceOver(mutableListOf()) { request ->
            if ("/dienstleistung/" in request.url.encodedPath) HttpStatusCode.OK to "<html>service</html>"
            else HttpStatusCode.Forbidden to blocked
        }

        val page = source.fetchCalendar(ServiceQuery(120882))

        assertEquals(403, page.httpStatus)
        assertEquals(Status.BLOCKED, classify(page).status)
    }
}
