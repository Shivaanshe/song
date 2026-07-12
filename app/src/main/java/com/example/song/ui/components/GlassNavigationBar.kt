package com.example.song.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Discover : Screen("discover", "Coming Soon", Icons.Default.MusicNote)
    object Favorites : Screen("favorites", "Favorites", Icons.Default.Favorite)
    object Library : Screen("library", "Library", Icons.Default.CloudDownload)
}

@Composable
fun GlassNavigationBar(navController: NavController) {
    val items = listOf(
        Screen.Discover,
        Screen.Favorites,
        Screen.Library
    )

    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry.value?.destination

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(32.dp))
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(32.dp)),
        color = Color.White.copy(alpha = 0.2f),
        tonalElevation = 0.dp
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            modifier = Modifier.height(64.dp),
            windowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            items.forEach { screen ->
                val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.title,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = null,
                    selected = selected,
                    onClick = {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFE91E63),
                        unselectedIconColor = Color(0xFF424242),
                        indicatorColor = Color.White.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}
