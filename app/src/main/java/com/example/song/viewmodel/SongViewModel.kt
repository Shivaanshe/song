package com.example.song.viewmodel

import android.app.Application
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
import com.example.song.data.database.AppDatabase
import com.example.song.data.model.Playlist
import com.example.song.data.model.Song
import com.example.song.data.repository.SongRepository
import com.example.song.service.MusicService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class SongViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SongRepository
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

    private var isUserSeeking = false

    val filteredSongs = combine(_allSongs, _searchQuery) { songs, query ->
        if (query.isBlank()) songs
        else songs.filter { 
            it.title.contains(query, ignoreCase = true) || 
            it.artist.contains(query, ignoreCase = true) 
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        val database = AppDatabase.getDatabase(application)
        repository = SongRepository(
            database.songDao(),
            database.playlistDao(),
            application.filesDir // Use filesDir for persistent storage
        )

        viewModelScope.launch {
            repository.allSongs.collect { _allSongs.value = it }
        }
        viewModelScope.launch {
            repository.allPlaylists.collect { _playlists.value = it }
        }
        viewModelScope.launch {
            repository.favoriteSongs.collect { _favoriteSongs.value = it }
        }
        initMediaController(application)
        startProgressUpdate()
    }

    private fun initMediaController(context: Context) {
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            mediaController = future.get().apply {
                addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        mediaItem?.let { item ->
                            val songId = item.mediaId.toIntOrNull()
                            _currentPlayingSong.value = _currentQueue.value.find { it.id == songId }
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
                        }
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
                    _allSongs.value.find { it.id == songId }
                }
            }
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
            repository.updateSong(song.copy(isFavorite = isFavorite))
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

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
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
            setMediaItems(queue.map { it.toMediaItem() }, index, 0L)
            repeatMode = currentMode
            prepare()
            play()
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

    fun downloadFromYoutube(url: String) {
        viewModelScope.launch {
            // Check if engine is ready
            val isEngineReady = com.example.song.SongApplication.getInstance().isReady.value
            if (!isEngineReady) {
                _downloadState.value = DownloadState.Error("Music engine is still initializing. Please wait.")
                delay(3000)
                _downloadState.value = DownloadState.Idle
                return@launch
            }

            _downloadState.value = DownloadState.Downloading(0f)
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
        mediaController?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    override fun onCleared() {
        mediaController?.release()
        super.onCleared()
    }
}

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}
