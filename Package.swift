// swift-tools-version:5.9
//
// Swift Package wrapping the Kotlin Multiplatform library as a binary XCFramework.
//
// Build the framework first (on a Mac), then add this package to Xcode:
//
//     ./gradlew assembleTerminCoreXCFramework
//     # Xcode -> File -> Add Package Dependencies... -> Add Local... -> pick this repo
//
// The XCFramework is a build output, so it is not in git: a fresh clone must run the Gradle task
// before Xcode can resolve this package.
import PackageDescription

let package = Package(
    name: "TerminCore",
    platforms: [.iOS(.v14)],
    products: [
        .library(name: "TerminCore", targets: ["TerminCore"]),
    ],
    targets: [
        .binaryTarget(
            name: "TerminCore",
            path: "build/XCFrameworks/release/TerminCore.xcframework"
        ),
    ]
)
