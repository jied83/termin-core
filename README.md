# termin-core

Reads free appointment dates from [service.berlin.de](https://service.berlin.de)


## The API

```kotlin
val client = TerminCore.client(engine = OkHttp)   // Darwin on iOS
val result = client.check(
    query = ServiceQuery(serviceId = 120882),
    knownDates = myStore.load(),        // ISO dates seen last time; the library stores nothing
    filter = DateFilter(weekdays = setOf(DayOfWeek.FRIDAY)),
)
result.status        // AVAILABLE / NONE / BLOCKED / UNKNOWN
result.dates         // every free date, ISO
result.freshDates    // the ones knownDates had not seen -> what is worth an alert
result.matches       // fresh AND passing the filter
myStore.save(result.dates)
```

### Adding it to a JVM project

```kotlin
dependencies {
    implementation("io.github.jied83:termin-core:0.1.0")
    implementation("io.ktor:ktor-client-okhttp:3.6.0")
}
```

### Adding it to an Android app
TBA

### Adding it to an iOS project
TBA