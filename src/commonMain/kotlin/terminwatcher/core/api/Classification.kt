package terminwatcher.core.api

import terminwatcher.core.internal.classifyPage

fun classify(page: FetchedPage): PageResult =
    classifyPage(page.html, page.finalUrl, page.httpStatus)
