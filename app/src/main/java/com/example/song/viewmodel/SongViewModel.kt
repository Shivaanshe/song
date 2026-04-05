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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SongViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SongRepository
    private var mediaController: MediaController? = null

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

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

    init {
        val database = AppDatabase.getDatabase(application)
        repository = SongRepository(database.songDao(), database.playlistDao())

        viewModelScope.launch {
            repository.allSongs.collect { _allSongs.value = it }
        }
        viewModelScope.launch {
            repository.allPlaylists.collect { _playlists.value = it }
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

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            _duration.value = duration
                        }
                    }
                })
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun startProgressUpdate() {
        viewModelScope.launch {
            while (isActive) {
                mediaController?.let {
                    _currentPosition.value = it.currentPosition
                }
                delay(1000)
            }
        }
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
    }

    fun addSong(song: Song) {
        viewModelScope.launch {
            repository.insertSong(song)
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
            setMediaItems(queue.map { it.toMediaItem() }, index, 0L)
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

    private fun Song.toMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(audioUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(Uri.parse(imageUrl ?: ""))
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
