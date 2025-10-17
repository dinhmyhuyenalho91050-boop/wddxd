# Android 15 Tooling Compatibility Notes

* According to the official Android Gradle plugin compatibility table, targeting API level 35 (Android 15) requires at least Android Gradle plugin 8.6.0 and Android Studio Koala Feature Drop 2024.2.1 or newer.
* The same table notes that AGP 8.6.x expects Gradle 8.7 or higher, so the project wrapper should point at Gradle 8.7+ when building for Android 15.
* Binary artifacts are not committed; instead the Gradle wrapper scripts download `gradle-wrapper.jar` (with checksum verification) on demand so repositories that disallow binaries can still build successfully.
