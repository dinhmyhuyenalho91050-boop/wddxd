# Android 15 Tooling Compatibility Notes

* According to the official Android Gradle plugin compatibility table, targeting API level 36 (Android 15) requires Android Gradle plugin 8.9.1 alongside Android Studio Koala Feature Drop 2024.3.1 or newer.
* AGP 8.9.1 mandates Gradle 8.11+, so the project wrapper tracks Gradle 8.11.1 to satisfy that requirement when building against API level 36.
* Binary artifacts are not committed; instead the Gradle wrapper scripts download `gradle-wrapper.jar` (with checksum verification) on demand so repositories that disallow binaries can still build successfully.
