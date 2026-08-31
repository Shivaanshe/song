# Pulse Music 🎵

Pulse is a high-performance, resilient Android music streaming engine built on modern Android standards (Media3, Jetpack Compose). It features a **"Final Form" JIT (Just-In-Time) resolution architecture** that provides a seamless, premium listening experience across YouTube and local storage.



## 🚀 Architectural Highlights

### 1. JIT Resolution Engine (`ResolvingDataSource`)
Pulse uses a professional-grade **Just-In-Time resolution pipeline**. Instead of pre-resolving entire playlists (which is slow and wastes data), Pulse natively "pauses" the network request at the last millisecond to fetch the real stream URL and authenticated headers.
- **Zero-Gap Playback:** 100% native Media3 queue transitions.
- **Protocol Agnostic:** Automatically handles `http`, `https`, and `file://` protocols for seamless hybrid playback (Streaming + Downloads).

### 2. "Burner Thread" Resilience
To handle native JNI deadlocks (common in network extraction), Pulse implements a **Burner Thread Architecture**:
- **Detached Watchdog:** Extractions run in supervised "burner" coroutines.
- **Hard-Kill Protocol:** If a process hangs for more than 35 seconds, a parallel watcher physically terminates the native process to release the Mutex and free the engine.
- **Circuit Breaker:** Automatically trips after 10 consecutive failures to protect device resources and data.

### 3. Zero-Lag Metadata Injection
Pulse provides a premium system-level experience without performance hits:
- **Raw Byte Artwork:** Album art is fetched in parallel with the audio and injected as raw bytes directly into the `MediaMetadata`.
- **Dynamic Theming:** The notification tray and lock screen change colors instantly to match the album art with 0ms network lag at playback start.

### 4. High-Resolution Debugging
Built-in **Pulse Debugger** provides real-time telemetry:
- **Live Cache Monitor:** Track LRU (Least Recently Used) eviction and cache hits.
- **JIT Timeline:** High-fidelity logging of the resolution handshake.
- **Performance Toggles:** Hardware-friendly controls like the "Background Orbs" toggle for low-end devices.

## 🛠️ Tech Stack
- **UI:** Jetpack Compose (Material 3)
- **Engine:** Media3 (ExoPlayer) / Session
- **Networking:** OkHttp3 / ResolvingDataSource
- **Extraction:** youtubedl-android (yt-dlp) / FFmpeg
- **Image Loading:** Coil
- **Concurrency:** Kotlin Coroutines & Flows (StateFlow/SharedFlow)

## 📦 Getting Started
1. Clone the repository.
2. Ensure you have the latest Android Studio installed.
3. Pulse automatically initializes and updates the `yt-dlp` binary on the first launch.
4. Grant the required permissions (Media/Storage) to begin scanning your library.

## 🛡️ Security & Stability
- **Triple-Lock Handshake:** Dynamic injection of User-Agent, Referer, and Cookies to prevent `403 Forbidden` errors.
- **Priority Queueing:** Active tracks always jump to the front of the extraction line, while background pre-fetches wait for a 1.5s priority delay.

---
*Developed with a focus on stability, performance, and a premium Android experience.*

## 📖 How to Use

### Adding Songs
- **Discover Page:** When you add a song from the Discover page, Pulse utilizes its **JIT Resolution Engine** to fetch the highest quality stream directly from the source. The song is added to your active session and can be instantly favorited.
- **My Library:** Songs in My Library represent your local collection. Pulse scans your device storage and integrates these files seamlessly into the player, allowing for a unified listening experience between local files and online streams.

### Key Features
- **Glassmorphism UI:** A premium, modern interface with fluid "water-like" animations and adaptive landscape layouts.
- **Fluid Navigation:** Navigation bars featuring smooth, synchronized bubble animations that follow your interaction in real-time.
- **Adaptive Landscape:** A specialized layout for landscape mode that includes a floating minimal sidebar and an optimized player pane for ease of use.
- **Smart JIT Loading:** Just-In-Time stream resolution ensures minimal data usage and maximum reliability.
- **Pulse Debugger:** Real-time telemetry and performance monitoring built right into the app.

### Performance Note
- **Loading Time:** New songs typically take **3-4 seconds** to load. This includes the JIT resolution process, authenticated handshake, and initial buffering to ensure a skip-free listening experience.
