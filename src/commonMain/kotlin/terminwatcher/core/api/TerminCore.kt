package terminwatcher.core.api

import io.ktor.client.engine.HttpClientEngineFactory
import kotlinx.datetime.TimeZone
import terminwatcher.core.internal.DefaultTerminClient
import terminwatcher.core.internal.HttpPageSource
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

const val DEFAULT_USER_AGENT: String =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

val DEFAULT_DWELL: Duration = 5.seconds

val DEFAULT_REQUEST_TIMEOUT: Duration = 45.seconds

object TerminCore {

    fun pageSource(
        engine: HttpClientEngineFactory<*>,
        dwell: Duration = DEFAULT_DWELL,
        requestTimeout: Duration = DEFAULT_REQUEST_TIMEOUT,
        userAgent: String = DEFAULT_USER_AGENT,
    ): PageSource = HttpPageSource(engine, dwell, requestTimeout, userAgent)

    fun client(
        engine: HttpClientEngineFactory<*>,
        dwell: Duration = DEFAULT_DWELL,
        requestTimeout: Duration = DEFAULT_REQUEST_TIMEOUT,
        userAgent: String = DEFAULT_USER_AGENT,
        clock: Clock = Clock.System,
        timeZone: TimeZone = BERLIN,
    ): TerminClient = client(pageSource(engine, dwell, requestTimeout, userAgent), clock, timeZone)

    fun client(
        source: PageSource,
        clock: Clock = Clock.System,
        timeZone: TimeZone = BERLIN,
    ): TerminClient = DefaultTerminClient(source, clock, timeZone)
}
