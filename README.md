# Pulse Music 🎵

Pulse is a high-performance, resilient Android music streaming engine built on modern Android standards (Media3, Jetpack Compose). It features a **"Final Form" JIT (Just-In-Time) resolution architecture** that provides a seamless, premium listening experience across YouTube and local storage.

<p align="center">
  <img src="https://raw.githubusercontent.com/user-attachments/assets/8172960a-4933-4f9e-bc43-26464522964b" width="30%" alt="Now Playing Screen">
  <img src="https://raw.githubusercontent.com/user-attachments/assets/7594967a-2514-4634-874d-96541622964b" width="30%" alt="Notification Bar">
</p>

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
