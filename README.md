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

### Adding & Managing Music
- **Discover Page (Smart Cloud Streaming):** When you add or favorite a song from Discover, Pulse saves only the lightweight track link and metadata to preserve your device storage. When you press play, the Just-In-Time (JIT) Resolution Engine dynamically fetches the highest quality audio stream and temporarily buffers it in a rolling cache. Once playback finishes, temporary streaming cache is automatically recycled to keep your phone running light and fast.
- **My Library (Local Storage & Downloads):** This is your permanent offline hub. Tracks you explicitly choose to download are saved directly to your phone's local storage for zero-data offline playback. Pulse automatically indexes your device storage, seamlessly merging downloaded music and local audio files into one unified collection.

### Key Features
- **Glassmorphic Aesthetic:** Translucent, glass-textured interface featuring dynamic scroll-reactive color shifting and fluid, tactile drag-and-drop playlist reordering.
- **Adaptive Landscape Mode:** Automatically reorganizes on screen rotation into a widescreen two-pane layout—anchoring artwork and playback controls on the left with a full scrollable tracklist on the right via a slim vertical Navigation Rail.
- **Zero-Bloat JIT Streaming:** On-demand stream resolution eliminates storage buildup by avoiding unnecessary full-file downloads for cloud tracks.
- **Pulse Debugger:** Built-in real-time telemetry and system diagnostics to monitor network bandwidth, active threads, and media cache allocation.

### Playback & Performance
- **Initial Stream Handshake:** Cloud-resolved songs take roughly 2 to 4 seconds to initiate. This brief window allows the JIT engine to negotiate the source handshake, extract audio streams, and pre-fill the playback buffer to guarantee skip-free listening.
- **Instant Local Skips:** Songs stored locally in My Library bypass the network resolution stage entirely, playing instantly with zero latency.
