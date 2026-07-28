package com.example.song

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.song.ui.components.DebugOverlay
import com.example.song.ui.components.NowPlayingBar
import com.example.song.ui.screens.LibraryScreen
import com.example.song.ui.screens.PlayerScreen
import com.example.song.ui.screens.PlaylistDetailScreen
import com.example.song.ui.theme.SongTheme
import com.example.song.viewmodel.SongViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SongViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

                LaunchedEffect(hasPermission) {
                    if (!hasPermission) {
                        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.READ_MEDIA_AUDIO
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                        permissionLauncher.launch(permission)
                    } else {
                        viewModel.initMediaController(this@MainActivity)
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

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFD1C4E9),
            Color(0xFFBBDEFB)
        )
    )

    // Screens where bottom bar should be visible
    val mainScreens = listOf("library", "favorites", "discover")
    val showBottomBar = currentDestination?.route in mainScreens

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    if (currentDestination?.route != "player" && currentSong != null) {
                        NowPlayingBar(
                            song = currentSong,
                            isPlaying = isPlaying,
                            onTogglePlay = { viewModel.togglePlayPause() },
                            onClick = { navController.navigate("player") }
                        )
                    }
                    if (showBottomBar) {
                        com.example.song.ui.components.GlassNavigationBar(navController)
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = "library",
                    modifier = Modifier.padding(
                        if (currentDestination?.route == "player") PaddingValues() 
                        else innerPadding
                    )
                ) {
                    composable("library") {
                        LibraryScreen(
                            viewModel = viewModel,
                            onPlaylistClick = { navController.navigate("playlist/${it.id}/${it.name}") },
                            onFavoritesClick = { navController.navigate("favorites") },
                            onSongClick = { navController.navigate("player") }
                        )
                    }
                    composable("favorites") {
                        com.example.song.ui.screens.FavoritesScreen(
                            viewModel = viewModel,
                            onSongClick = { navController.navigate("player") }
                        )
                    }
                    composable("discover") {
                        com.example.song.ui.screens.DiscoverScreen(
                            viewModel = viewModel,
                            onSongClick = { navController.navigate("player") }
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
                            viewModel = viewModel,
                            onBackClick = { navController.popBackStack() },
                            onSongClick = { navController.navigate("player") }
                        )
                    }
                    composable("player") {
                        PlayerScreen(
                            viewModel = viewModel,
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
                
                // Floating Debug Button on top of everything
                DebugOverlay(viewModel)
            }
        }
    }
}
