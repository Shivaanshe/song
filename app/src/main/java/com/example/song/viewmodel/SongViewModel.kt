package com.example.song.viewmodel

import android.app.Application
import android.util.Log
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.song.data.api.SpotifyResponse
import com.example.song.data.database.AppDatabase
import com.example.song.data.model.Playlist
import com.example.song.data.model.Song
import com.example.song.data.model.StreamingItem
import com.example.song.data.repository.SongRepository
import com.example.song.service.MusicService
import com.example.song.util.PulseLogger
import com.example.song.util.YoutubeStreamHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SongViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SongRepository = SongRepository(
        AppDatabase.getDatabase(application).songDao(),
        AppDatabase.getDatabase(application).playlistDao(),
        AppDatabase.getDatabase(application).streamingDao(),
        application.filesDir
    )
    private var mediaController: MediaController? = null

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _favoriteSongs = MutableStateFlow<List<Song>>(emptyList())
    val favoriteSongs: StateFlow<List<Song>> = _favoriteSongs.asStateFlow()

    private val _currentPlayingSong = MutableStateFlow<Song?>(null)
    val currentPlayingSong: StateFlow<Song?> = _currentPlayingSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _topLevelStreamingItems = MutableStateFlow<List<StreamingItem>>(emptyList())
    val topLevelStreamingItems: StateFlow<List<StreamingItem>> = _topLevelStreamingItems.asStateFlow()

    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    private val _pendingStreamingItems = MutableStateFlow<List<StreamingItem>>(emptyList())
    val pendingStreamingItems: StateFlow<List<StreamingItem>> = _pendingStreamingItems.asStateFlow()

    private val _pendingDownloadItems = MutableStateFlow<List<StreamingItem>>(emptyList())
    val pendingDownloadItems: StateFlow<List<StreamingItem>> = _pendingDownloadItems.asStateFlow()

    private val _resolvingUrlId = MutableStateFlow<Int?>(null)
    val resolvingUrlId: StateFlow<Int?> = _resolvingUrlId.asStateFlow()

    private val _extractionError = MutableStateFlow<String?>(null)
    val extractionError: StateFlow<String?> = _extractionError.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    val systemLogs: StateFlow<List<String>> = PulseLogger.logs
    val currentTask: StateFlow<String?> = PulseLogger.currentTask
    val cachedKeys: StateFlow<Set<String>> = com.example.song.SongApplication.getInstance().cachedKeys

    private val _selectedSongIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedSongIds: StateFlow<Set<Int>> = _selectedSongIds.asStateFlow()

    private val _selectedStreamingIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedStreamingIds: StateFlow<Set<Int>> = _selectedStreamingIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = combine(_selectedSongIds, _selectedStreamingIds) { songs, streaming ->
        songs.isNotEmpty() || streaming.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var isUserSeeking = false

    val filteredSongs = combine(
        _allSongs, 
        _searchQuery, 
        repository.allSongIdsInPlaylists
    ) { songs, query, idsInPlaylists ->
        val playlistIds = idsInPlaylists.toSet()
        val librarySongs = songs.filter { !playlistIds.contains(it.id) }
        
        if (query.isBlank()) librarySongs
        else librarySongs.filter { 
            it.title.contains(query, ignoreCase = true) || 
            it.artist.contains(query, ignoreCase = true) 
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            repository.allSongs.collect { songs ->
                _allSongs.value = songs
                // Update current playing song from the latest list to reflect changes (like favorite status)
                val currentId = _currentPlayingSong.value?.id 
                    ?: mediaController?.currentMediaItem?.mediaId?.toIntOrNull()
                
                if (currentId != null) {
                    songs.find { it.id == currentId }?.let { updated ->
                        _currentPlayingSong.value = updated
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.allPlaylists.collect { _playlists.value = it }
        }
        viewModelScope.launch {
            repository.favoriteSongs.collect { _favoriteSongs.value = it }
        }
        viewModelScope.launch {
            repository.topLevelStreamingItems.collect { items ->
                _topLevelStreamingItems.value = items.filter { it.isPlaylist || it.parentPlaylistUrl == null }
            }
        }

        /* viewModelScope.launch {
            repository.scanAndRestoreSongs()
        } */
        
        initMediaController(application)
    }

    fun getItemsForStreamingPlaylist(playlistUrl: String): Flow<List<StreamingItem>> {
        return repository.getItemsForStreamingPlaylist(playlistUrl)
    }

    fun fetchStreamingMetadata(url: String) {
        viewModelScope.launch {
            _isExtracting.value = true
            _extractionError.value = null
            try {
                var spotifyMeta: SpotifyResponse? = null
                val finalUrl = if (url.contains("spotify.com")) {
                    try {
                        val meta = repository.resolveSpotifyMetadata(url)
                        spotifyMeta = meta
                        
                        val query = if (url.contains("/track/")) {
                            "${meta.artist} - ${meta.title}"
                        } else {
                            // Album or Playlist - search for the title as a playlist candidate
                            "${meta.title} playlist"
                        }
                        "ytsearch1:$query"
                    } catch (e: Exception) {
                        url 
                    }
                } else url

                val items = YoutubeStreamHandler.getMetadata(finalUrl)
                Log.d("SongViewModel", "Extraction results for $finalUrl: ${items.size} items")
                
                if (items.isEmpty()) {
                    _extractionError.value = if (url.contains("spotify.com")) "Could not find this track on YouTube" else "No videos found in this URL"
                    return@launch
                }

                // Enrichment for Spotify
                val processedItems = if (spotifyMeta != null) {
                    items.map { it.copy(
                        title = spotifyMeta.title,
                        thumbnailUrl = spotifyMeta.thumbnailUrl ?: it.thumbnailUrl
                    )}
                } else items

                // Identify collection if it has multiple items OR is explicitly a playlist URL
                val isCollection = processedItems.any { it.isPlaylist } || url.contains("/playlist/") || url.contains("/album/") || url.contains("list=")
                
                if (processedItems.size == 1 && !isCollection) {
                    // Single track, add immediately
                    repository.insertStreamingItems(processedItems)
                } else {
                    // Playlist, multiple items, or collection, show choice
                    _pendingStreamingItems.value = processedItems
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: ""
                _extractionError.value = when {
                    errorMsg.contains("429") -> "YouTube is rate-limiting requests. Please try again in a few minutes."
                    errorMsg.contains("confirm you're not a bot") -> "Bot detection triggered. Try a different link or wait."
                    else -> "Extraction failed: $errorMsg"
                }
                e.printStackTrace()
            } finally {
                _isExtracting.value = false
            }
        }
    }

    fun addPendingStreamingItems(asCollection: Boolean) {
        viewModelScope.launch {
            val items = _pendingStreamingItems.value
            if (items.isEmpty()) return@launch

            if (asCollection) {
                repository.insertStreamingItems(items)
            } else {
                // Discard playlist container and add videos as top-level
                val filteredItems = items.filter { !it.isPlaylist }.map { 
                    it.copy(parentPlaylistUrl = null) 
                }
                repository.insertStreamingItems(filteredItems)
            }
            _pendingStreamingItems.value = emptyList()
        }
    }

    fun clearPendingStreamingItems() {
        _pendingStreamingItems.value = emptyList()
    }

    fun deleteStreamingItem(item: StreamingItem) {
        viewModelScope.launch {
            repository.deleteStreamingItem(item)
        }
    }

    fun updateStreamingItemTitle(itemId: Int, newTitle: String) {
        viewModelScope.launch {
            repository.updateStreamingItemTitle(itemId, newTitle)
        }
    }

    fun addStreamingItems(items: List<StreamingItem>) {
        viewModelScope.launch {
            repository.insertStreamingItems(items)
        }
    }

    fun initMediaController(context: Context) {
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            mediaController = future.get().apply {
                addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        mediaItem?.let { item ->
                            val songId = item.mediaId.toIntOrNull()
                            _currentPlayingSong.value = _allSongs.value.find { it.id == songId } ?: _currentQueue.value.find { it.id == songId }
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION || reason == Player.DISCONTINUITY_REASON_SKIP) {
                            _currentPosition.value = 0L
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            _duration.value = duration
                            _playbackError.value = null
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e("SongViewModel", "Playback error: ${error.message}", error)
                        _playbackError.value = "Playback Error: ${error.localizedMessage}"
                        _isPlaying.value = false
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        _repeatMode.value = repeatMode
                    }
                })
                // Initial state sync
                _repeatMode.value = repeatMode
                _isPlaying.value = isPlaying
                _currentPlayingSong.value = currentMediaItem?.let { item ->
                    val songId = item.mediaId.toIntOrNull()
                    _allSongs.value.find { it.id == songId } ?: _currentQueue.value.find { it.id == songId }
                }
            }
            startProgressUpdate()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun startProgressUpdate() {
        viewModelScope.launch {
            while (isActive) {
                if (!isUserSeeking) {
                    mediaController?.let {
                        _currentPosition.value = it.currentPosition
                    }
                }
                delay(500)
            }
        }
    }

    fun setUserSeeking(seeking: Boolean) {
        isUserSeeking = seeking
    }

    fun updateSeekPosition(position: Long) {
        _currentPosition.value = position
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
        isUserSeeking = false
    }

    fun addSong(song: Song) {
        viewModelScope.launch {
            repository.insertSong(song)
        }
    }

    fun updateFavorite(song: Song, isFavorite: Boolean) {
        viewModelScope.launch {
            val updatedSong = song.copy(isFavorite = isFavorite)
            repository.updateSong(updatedSong)
            // Explicitly update currentPlayingSong if it's the one being modified
            // to ensure immediate UI feedback
            if (_currentPlayingSong.value?.id == song.id) {
                _currentPlayingSong.value = updatedSong
            }
        }
    }

    fun deleteSong(songId: Int) {
        viewModelScope.launch {
            repository.deleteSong(songId)
            if (_currentPlayingSong.value?.id == songId) {
                mediaController?.stop()
                _currentPlayingSong.value = null
            }
        }
    }

    private val _selectionRange = MutableStateFlow<SelectionRange?>(null)

    data class SelectionRange(
        val startId: Int,
        val initialSelectedIds: Set<Int>,
        val isAdding: Boolean
    )

    fun startRangeSelection(songId: Int, allItems: List<Int>, isStreaming: Boolean = false) {
        val currentSelected = if (isStreaming) _selectedStreamingIds.value else _selectedSongIds.value
        val isAdding = !currentSelected.contains(songId)
        
        _selectionRange.value = SelectionRange(songId, currentSelected, isAdding)
        
        // Initial state update
        updateRangeSelection(songId, allItems, isStreaming)
    }

    fun updateRangeSelection(currentId: Int, allItems: List<Int>, isStreaming: Boolean = false) {
        val range = _selectionRange.value ?: return

        if (allItems.isEmpty()) return

        val startIndex = allItems.indexOf(range.startId)
        val currentIndex = allItems.indexOf(currentId)

        if (startIndex == -1 || currentIndex == -1) return

        val fromIndex = minOf(startIndex, currentIndex)
        val toIndex = maxOf(startIndex, currentIndex)
        val rangeIds = allItems.subList(fromIndex, toIndex + 1).toSet()

        if (isStreaming) {
            val newSelection = if (range.isAdding) {
                range.initialSelectedIds + rangeIds
            } else {
                range.initialSelectedIds - rangeIds
            }
            _selectedStreamingIds.value = newSelection
        } else {
            val newSelection = if (range.isAdding) {
                range.initialSelectedIds + rangeIds
            } else {
                range.initialSelectedIds - rangeIds
            }
            _selectedSongIds.value = newSelection
        }
    }

    fun endRangeSelection() {
        _selectionRange.value = null
    }

    fun toggleSelectionMode(enabled: Boolean) {
        if (!enabled) {
            _selectedSongIds.value = emptySet()
            _selectedStreamingIds.value = emptySet()
        }
    }

    fun toggleSongSelection(songId: Int) {
        val current = _selectedSongIds.value.toMutableSet()
        if (current.contains(songId)) {
            current.remove(songId)
        } else {
            current.add(songId)
        }
        _selectedSongIds.value = current
    }

    fun toggleStreamingSelection(itemId: Int) {
        val current = _selectedStreamingIds.value.toMutableSet()
        if (current.contains(itemId)) {
            current.remove(itemId)
        } else {
            current.add(itemId)
        }
        _selectedStreamingIds.value = current
    }

    fun selectSong(songId: Int) {
        val current = _selectedSongIds.value.toMutableSet()
        if (current.add(songId)) {
            _selectedSongIds.value = current
        }
    }

    fun selectStreamingItem(itemId: Int) {
        val current = _selectedStreamingIds.value.toMutableSet()
        if (current.add(itemId)) {
            _selectedStreamingIds.value = current
        }
    }

    fun deleteSelectedItems() {
        viewModelScope.launch {
            val songIds = _selectedSongIds.value.toList()
            val streamingIds = _selectedStreamingIds.value.toList()

            songIds.forEach { id ->
                repository.deleteSong(id)
                if (_currentPlayingSong.value?.id == id) {
                    mediaController?.stop()
                    _currentPlayingSong.value = null
                }
            }

            streamingIds.forEach { id ->
                repository.deleteStreamingItemById(id)
            }

            toggleSelectionMode(false)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlistId: Int) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun addSongToPlaylist(songId: Int, playlistId: Int) {
        viewModelScope.launch {
            repository.addSongToPlaylist(songId, playlistId)
        }
    }

    fun removeSongFromPlaylist(songId: Int, playlistId: Int) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(songId, playlistId)
        }
    }

    fun removeSelectedFromPlaylist(playlistId: Int) {
        viewModelScope.launch {
            val songIds = _selectedSongIds.value.toList()
            songIds.forEach { id ->
                repository.removeSongFromPlaylist(id, playlistId)
            }
            toggleSelectionMode(false)
        }
    }

    fun getSongsInPlaylist(playlistId: Int): Flow<List<Song>> {
        return repository.getSongsInPlaylist(playlistId)
    }

    fun playSong(song: Song, queue: List<Song> = _allSongs.value) {
        if (queue.isEmpty()) return
        
        _currentQueue.value = queue
        _currentPlayingSong.value = song
        
        val index = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        
        mediaController?.apply {
            // Respect the current repeat mode when setting new media items
            val currentMode = repeatMode 
            val items = queue.map { it.toMediaItem() }
            setMediaItems(items, index, 0L)
            repeatMode = currentMode
            prepare()
            play()
        }
    }

    private fun StreamingItem.toMediaItem(directUrl: String? = null): MediaItem {
        val bundle = android.os.Bundle().apply {
            putString("youtube_url", youtubeUrl)
        }
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(directUrl ?: youtubeUrl) // Placeholder, Service will resolve this if it's the youtubeUrl
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist("YouTube")
                    .setArtworkUri(thumbnailUrl?.let { Uri.parse(it) })
                    .setExtras(bundle)
                    .build()
            )
            .build()
    }

    fun playStreamingItem(item: StreamingItem, queue: List<StreamingItem>) {
        viewModelScope.launch {
            _resolvingUrlId.value = item.id
            val directUrl = YoutubeStreamHandler.getDirectAudioUrl(item.youtubeUrl)
            _resolvingUrlId.value = null
            
            if (directUrl == null) {
                _playbackError.value = "Could not resolve audio link."
                return@launch
            }

            val filteredQueue = queue.filter { !it.isPlaylist }
            val index = filteredQueue.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
            
            // Set the first item with a direct URL, the rest as placeholders
            val mediaItems = filteredQueue.map { qItem ->
                if (qItem.id == item.id) {
                    qItem.toMediaItem(directUrl)
                } else {
                    qItem.toMediaItem()
                }
            }

            _currentQueue.value = filteredQueue.map {
                Song(
                    id = it.id,
                    title = it.title,
                    artist = "YouTube",
                    audioUri = it.youtubeUrl,
                    imageUrl = it.thumbnailUrl,
                    duration = it.duration
                )
            }
            _currentPlayingSong.value = _currentQueue.value.getOrNull(index)
            _isPlaying.value = true

            mediaController?.apply {
                val currentMode = repeatMode
                setMediaItems(mediaItems, index, 0L)
                repeatMode = currentMode
                prepare()
                play()
            }
        }
    }

    fun skipToNext() {
        mediaController?.seekToNext()
    }

    fun skipToPrevious() {
        mediaController?.let {
            if (it.currentPosition > 3000) {
                it.seekTo(0L)
            } else {
                it.seekToPrevious()
            }
        }
    }

    fun toggleRepeatMode() {
        mediaController?.let {
            val nextMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL // Repeat All (Green)
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE // Repeat One (Green with 1)
                else -> Player.REPEAT_MODE_OFF // Repeat Off (Gray)
            }
            it.repeatMode = nextMode
            _repeatMode.value = nextMode
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun fetchDownloadMetadata(url: String) {
        viewModelScope.launch {
            _isExtracting.value = true
            try {
                var spotifyMeta: SpotifyResponse? = null
                val finalUrl = if (url.contains("spotify.com")) {
                    try {
                        val meta = repository.resolveSpotifyMetadata(url)
                        spotifyMeta = meta
                        
                        val query = if (url.contains("/track/")) {
                            "${meta.artist} - ${meta.title}"
                        } else {
                            "${meta.title} playlist"
                        }
                        "ytsearch1:$query"
                    } catch (e: Exception) {
                        url
                    }
                } else if (url.contains("list=")) {
                    val listId = url.substringAfter("list=").substringBefore("&")
                    "https://www.youtube.com/playlist?list=$listId"
                } else url

                val items = YoutubeStreamHandler.getMetadata(finalUrl)
                
                if (items.isEmpty()) {
                    if (url.contains("spotify.com")) {
                         _downloadState.value = DownloadState.Error("Could not find this track on YouTube")
                         delay(3000)
                         _downloadState.value = DownloadState.Idle
                    }
                    return@launch
                }

                // Enrichment for Spotify
                val processedItems = if (spotifyMeta != null) {
                    items.map { it.copy(
                        title = spotifyMeta.title,
                        thumbnailUrl = spotifyMeta.thumbnailUrl ?: it.thumbnailUrl
                    )}
                } else items

                // Identify collection
                val isCollection = processedItems.any { it.isPlaylist } || url.contains("/playlist/") || url.contains("/album/") || url.contains("list=")
                
                if (processedItems.size == 1 && !isCollection) {
                    // Single track, download immediately
                    downloadFromYoutube(processedItems[0].youtubeUrl)
                } else {
                    // Playlist, multiple items, or collection, show choice
                    _pendingDownloadItems.value = processedItems
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isExtracting.value = false
            }
        }
    }

    fun startBatchDownload(asPlaylist: Boolean) {
        viewModelScope.launch {
            // Self-Heal Improvement: Wait for engine readiness
            val isReadyFlow = com.example.song.SongApplication.getInstance().isReady
            if (!isReadyFlow.value) {
                _downloadState.value = DownloadState.Downloading(0f)
                try {
                    withTimeout(15000) {
                        isReadyFlow.first { it }
                    }
                } catch (e: Exception) {
                    _downloadState.value = DownloadState.Error("Music engine failed to initialize.")
                    delay(3000)
                    _downloadState.value = DownloadState.Idle
                    return@launch
                }
            }

            val items = _pendingDownloadItems.value.filter { !it.isPlaylist }
            if (items.isEmpty()) return@launch

            val playlistItem = _pendingDownloadItems.value.find { it.isPlaylist }
            val playlistId = if (asPlaylist) {
                val name = playlistItem?.title ?: "Downloaded Playlist"
                repository.createPlaylist(name)
            } else {
                null
            }

            _pendingDownloadItems.value = emptyList()
            
            _downloadState.value = DownloadState.Downloading(0f, 1, items.size)
            
            items.forEachIndexed { index, item ->
                try {
                    _downloadState.value = DownloadState.Downloading(0f, index + 1, items.size)
                    repository.downloadYouTubeAudio(item.youtubeUrl, playlistId) { progress, _ ->
                        _downloadState.value = DownloadState.Downloading(progress, index + 1, items.size)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SongViewModel", "Failed to download ${item.title}", e)
                }
            }
            
            _downloadState.value = DownloadState.Success
            delay(3000)
            _downloadState.value = DownloadState.Idle
        }
    }

    fun clearPendingDownloadItems() {
        _pendingDownloadItems.value = emptyList()
    }

    fun downloadFromYoutube(url: String) {
        viewModelScope.launch {
            // Self-Heal Improvement: Wait for engine readiness instead of failing immediately
            val isReadyFlow = com.example.song.SongApplication.getInstance().isReady
            if (!isReadyFlow.value) {
                _downloadState.value = DownloadState.Downloading(0f) // Show preparing state
                try {
                    withTimeout(15000) { // Wait up to 15 seconds
                        isReadyFlow.first { it }
                    }
                } catch (e: Exception) {
                    _downloadState.value = DownloadState.Error("Music engine failed to initialize. Please restart.")
                    delay(3000)
                    _downloadState.value = DownloadState.Idle
                    return@launch
                }
            }

            try {
                repository.downloadYouTubeAudio(url) { progress, _ ->
                    _downloadState.value = DownloadState.Downloading(progress)
                }
                _downloadState.value = DownloadState.Success
                delay(3000)
                _downloadState.value = DownloadState.Idle
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException || e.message == "Download cancelled") {
                    _downloadState.value = DownloadState.Idle
                } else {
                    _downloadState.value = DownloadState.Error(e.message ?: "Unknown error")
                    delay(3000)
                    _downloadState.value = DownloadState.Idle
                }
            }
        }
    }

    fun cancelDownload() {
        repository.cancelDownload()
        _downloadState.value = DownloadState.Idle
    }

    fun resetDownloadState() {
        _downloadState.value = DownloadState.Idle
    }

    private fun Song.toMediaItem(): MediaItem {
        val uri = if (audioUri.startsWith("content://") || audioUri.startsWith("http")) {
            Uri.parse(audioUri)
        } else {
            Uri.fromFile(File(audioUri))
        }

        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(imageUrl?.let { Uri.parse(it) })
                    .build()
            )
            .build()
    }

    fun togglePlayPause() {
        android.util.Log.d("SongViewModel", "togglePlayPause triggered. isPlaying: ${mediaController?.isPlaying}, state: ${mediaController?.playbackState}")
        _playbackError.value = null
        mediaController?.let {
            when {
                it.isPlaying -> it.pause()
                it.playbackState == Player.STATE_IDLE -> {
                    it.prepare()
                    it.play()
                }
                it.playbackState == Player.STATE_ENDED -> {
                    it.seekTo(0)
                    it.play()
                }
                else -> it.play()
            }
        }
    }

    fun updateTask(task: String?) {
        PulseLogger.updateTask(task)
    }

    fun clearPlaybackError() {
        _playbackError.value = null
        PulseLogger.clear()
    }

    override fun onCleared() {
        mediaController?.release()
        super.onCleared()
    }
}

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val progress: Float,
        val current: Int = 1,
        val total: Int = 1
    ) : DownloadState()
    object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}
