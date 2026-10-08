package terminwatcher.core

import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import terminwatcher.core.api.ServiceQuery
import terminwatcher.core.api.TerminCore

fun main(args: Array<String>) = runBlocking {
    val serviceId = args.firstOrNull()?.toIntOrNull() ?: error("usage: liveCheck <serviceId>")
    val engine = if (args.getOrNull(1) == "cio") CIO else OkHttp
    println("engine : $engine")
    val client = TerminCore.client(engine = engine)
    val query = ServiceQuery(serviceId)

    println("checking ${query.calendarUrl}")
    val result = client.check(query)

    println("status : ${result.status}")
    println("dates  : ${result.dates.size} -> ${result.dates.take(8)}${if (result.dates.size > 8) " ..." else ""}")
    println("landed : ${result.finalUrl}")
    if (result.detail.isNotEmpty()) println("detail : ${result.detail}")
}
