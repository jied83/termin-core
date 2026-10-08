package terminwatcher.core.api

import io.ktor.client.engine.darwin.Darwin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun darwinPageSource(): PageSource = TerminCore.pageSource(Darwin)

fun darwinPageSource(dwell: Duration, requestTimeout: Duration): PageSource =
    TerminCore.pageSource(Darwin, dwell, requestTimeout)

fun newTerminClient(): TerminClient = TerminCore.client(Darwin)

fun newTerminClient(dwell: Duration, requestTimeout: Duration): TerminClient =
    TerminCore.client(Darwin, dwell, requestTimeout)

fun durationOfSeconds(seconds: Double): Duration = seconds.seconds
