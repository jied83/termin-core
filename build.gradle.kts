import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
}

repositories { mavenCentral() }

val onMac = System.getProperty("os.name").startsWith("Mac")
val frameworkName = "TerminCore"

kotlin {
    jvm {
        // Android consumes this artifact too, so stay on a bytecode level Android accepts.
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    if (onMac) {
        val xcf = XCFramework(frameworkName)
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = frameworkName
                isStatic = true
                xcf.add(this)
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            implementation(libs.ksoup)
            api(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.okhttp)
        }
        if (onMac) {
            iosMain.dependencies { api(libs.ktor.client.darwin) }
        }
    }
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("termin-core")
            description.set(
                "Reads free appointment dates from service.berlin.de. Kotlin Multiplatform: " +
                    "no config files, no storage, no scheduling - every parameter is passed in."
            )
            url.set("https://github.com/jied83/termin-core")
            developers {
                developer { name.set("Vladimir Sh") }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/jied83/termin-core")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

tasks.register<JavaExec>("liveCheck") {
    group = "verification"
    description = "Runs one real check against service.berlin.de (network, not part of the test suite)"
    val testCompilation = kotlin.jvm().compilations.getByName("test")
    classpath = testCompilation.output.allOutputs + testCompilation.runtimeDependencyFiles!!
    mainClass.set("terminwatcher.core.LiveCheckKt")
    args(project.findProperty("service")?.toString() ?: "120686")
}
