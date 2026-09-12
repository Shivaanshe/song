package com.example.song.ui.spotlight

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TourStep(
    val stepIndex: Int,
    val title: String,
    val description: String,
    val targetPage: Int
) {
    STEP_1_SEARCH(
        stepIndex = 1,
        title = "Online Search Engine",
        description = "Search for any track, artist, or YouTube URL directly. Finds online music instantly.",
        targetPage = 0
    ),
    STEP_2_CACHE_ENGINE(
        stepIndex = 2,
        title = "Stream & Cache Engine",
        description = "Paste a YouTube/Spotify link or search directly. Pulse Music instantly streams the audio and caches it locally (up to 300MB using LRU). Next time you play it, it loads instantly from the cache without using data.",
        targetPage = 0
    ),
    STEP_3_OTA_UPDATES(
        stepIndex = 3,
        title = "Seamless OTA Updates",
        description = "Pulse Music checks for updates automatically in the background. When a new version is ready, you can install it directly inside the app—no need to visit GitHub.",
        targetPage = 0
    ),
    STEP_4_NAVIGATION(
        stepIndex = 4,
        title = "Your Local Storage",
        description = "Tap here to access downloaded songs, device tracks, and custom playlists.",
        targetPage = 0
    ),
    STEP_5_LIBRARY_IMPORT(
        stepIndex = 5,
        title = "Local Library & Playlists",
        description = "Create custom playlists or import existing audio files directly from your device storage. To save space, songs shorter than 10 minutes are prioritized for the offline cache.",
        targetPage = 2
    ),
    STEP_6_NOW_PLAYING(
        stepIndex = 6,
        title = "Now Playing",
        description = "Tap here to open the full player screen. You can view the current track, lyrics, and manage your active queue.",
        targetPage = 2
    ),
    STEP_7_ARRANGE_MODE(
        stepIndex = 7,
        title = "Arrange Mode",
        description = "Toggle Arrange Mode to reorder songs inside playlists or on the Discover screen. Long-press and drag any track up or down to customize your playlist order.",
        targetPage = 2
    );

    val totalSteps: Int get() = 7
}

class SpotlightController {
    private val _activeStep = MutableStateFlow<TourStep?>(null)
    val activeStep: StateFlow<TourStep?> = _activeStep.asStateFlow()

    private val _targetBounds = MutableStateFlow<Map<TourStep, Rect>>(emptyMap())
    val targetBounds: StateFlow<Map<TourStep, Rect>> = _targetBounds.asStateFlow()

    private val _isTransitioning = MutableStateFlow(false)
    val isTransitioning: StateFlow<Boolean> = _isTransitioning.asStateFlow()

    var onPageChangeRequested: ((Int) -> Unit)? = null
    var onTourFinished: (() -> Unit)? = null

    fun registerBounds(step: TourStep, rect: Rect) {
        if (rect.width > 0 && rect.height > 0) {
            _targetBounds.value = _targetBounds.value + (step to rect)
        }
    }

    fun setTransitioning(transitioning: Boolean) {
        _isTransitioning.value = transitioning
    }

    fun startTour() {
        _activeStep.value = TourStep.STEP_1_SEARCH
        onPageChangeRequested?.invoke(TourStep.STEP_1_SEARCH.targetPage)
    }

    fun nextStep() {
        val current = _activeStep.value ?: return
        val nextIndex = current.stepIndex + 1
        if (nextIndex > current.totalSteps) {
            finishTour()
        } else {
            val nextStep = TourStep.entries.find { it.stepIndex == nextIndex }
            if (nextStep != null) {
                _isTransitioning.value = true
                _activeStep.value = nextStep
                onPageChangeRequested?.invoke(nextStep.targetPage)
            }
        }
    }

    fun previousStep() {
        val current = _activeStep.value ?: return
        val prevIndex = current.stepIndex - 1
        if (prevIndex >= 1) {
            val prevStep = TourStep.entries.find { it.stepIndex == prevIndex }
            if (prevStep != null) {
                _isTransitioning.value = true
                _activeStep.value = prevStep
                onPageChangeRequested?.invoke(prevStep.targetPage)
            }
        }
    }

    fun skipTour() {
        finishTour()
    }

    private fun finishTour() {
        _activeStep.value = null
        onTourFinished?.invoke()
    }
}
