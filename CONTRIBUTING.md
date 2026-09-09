# Contributing to Pulse Music

Thank you for helping improve Pulse Music!

## Getting Started
1. Fork the repository and clone your fork.
2. Open the project in Android Studio (Ladybug or newer recommended).
3. Ensure the Android SDK platform tools and JDK 17+ are installed.
4. Build the project using `./gradlew assembleDebug` to verify your environment.

## Architecture Guidelines
- **UI:** Pure Jetpack Compose with Material 3 styling. Keep state hoisted into ViewModels.
- **Audio:** All playback logic routes through Jetpack Media3 (`MediaController` / `MediaSessionService`). Do not call raw Android `MediaPlayer`.
- **Background Tasks:** Long-running downloads and processing must run via `WorkManager` or managed foreground services to comply with strict background execution limits.
- **Code Style:** Avoid wildcard imports and remove unused imports before opening a PR.

## Submitting a PR
1. Create a branch for your feature or bugfix (`git checkout -b feature/my-feature`).
2. Verify the project builds cleanly without errors.
3. Open a Pull Request referencing any open issue it resolves.
