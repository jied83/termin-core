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

### Getting it from JitPack

Releases are built by [JitPack](https://jitpack.io/#jied83/termin-core) from git tags.
Add the JitPack in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```


### Adding it to a JVM project

```kotlin
dependencies {
    implementation("com.github.jied83.termin-core:termin-core:0.1.1")
    implementation("io.ktor:ktor-client-okhttp:3.6.0")
}
```

### Adding it to an Android app

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.github.jied83.termin-core:termin-core:0.1.1")
    implementation("io.ktor:ktor-client-okhttp:3.6.0")
}
```

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
```

### Adding it to an iOS project
TBA