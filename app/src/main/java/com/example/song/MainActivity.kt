package com.example.song

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.song.ui.components.NowPlayingBar
import com.example.song.ui.screens.LibraryScreen
import com.example.song.ui.screens.PlayerScreen
import com.example.song.ui.screens.PlaylistDetailScreen
import com.example.song.ui.screens.PlaylistsScreen
import com.example.song.ui.theme.SongTheme
import com.example.song.viewmodel.SongViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SongViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SongTheme {
                var hasPermission by remember {
                    mutableStateOf(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                        } else {
                            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                        }
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    hasPermission = isGranted
                }

                LaunchedEffect(Unit) {
                    if (!hasPermission) {
                        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.READ_MEDIA_AUDIO
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                        permissionLauncher.launch(permission)
                    }
                }

                if (hasPermission) {
                    MainApp(viewModel)
                } else {
                    Surface {
                        Text("Please grant storage permission to access songs.")
                    }
                }
            }
        }
    }
}

@Composable
fun MainApp(viewModel: SongViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    val currentSong by viewModel.currentPlayingSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    Scaffold(
        bottomBar = {
            if (currentDestination?.route != "player") {
                Column {
                    NowPlayingBar(
                        song = currentSong,
                        isPlaying = isPlaying,
                        onTogglePlay = { viewModel.togglePlayPause() },
                        onClick = { navController.navigate("player") }
                    )
                    NavigationBar {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Home, contentDescription = "Library") },
                            label = { Text("Library") },
                            selected = currentDestination?.route == "library",
                            onClick = { navController.navigate("library") }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.List, contentDescription = "Playlists") },
                            label = { Text("Playlists") },
                            selected = currentDestination?.route == "playlists",
                            onClick = { navController.navigate("playlists") }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "library",
            modifier = Modifier.padding(if (currentDestination?.route == "player") PaddingValues() else innerPadding)
        ) {
            composable("library") {
                val songs by viewModel.allSongs.collectAsState()
                LibraryScreen(
                    songs = songs,
                    onPlaySong = { viewModel.playSong(it, songs) },
                    onAddSong = { viewModel.addSong(it) },
                    onDeleteSong = { viewModel.deleteSong(it.id) }
                )
            }
            composable("playlists") {
                val playlists by viewModel.playlists.collectAsState()
                PlaylistsScreen(
                    playlists = playlists,
                    onCreatePlaylist = { viewModel.createPlaylist(it) },
                    onPlaylistClick = { navController.navigate("playlist/${it.id}/${it.name}") }
                )
            }
            composable(
                "playlist/{playlistId}/{playlistName}",
                arguments = listOf(
                    navArgument("playlistId") { type = NavType.IntType },
                    navArgument("playlistName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getInt("playlistId") ?: 0
                val playlistName = backStackEntry.arguments?.getString("playlistName") ?: ""
                PlaylistDetailScreen(
                    playlistId = playlistId,
                    playlistName = playlistName,
                    viewModel = viewModel
                )
            }
            composable("player") {
                PlayerScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
