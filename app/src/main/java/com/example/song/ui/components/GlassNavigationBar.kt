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
    object Discover : Screen("discover", "Discover", Icons.Default.MusicNote)
    object Favorites : Screen("favorites", "Favorites", Icons.Default.Favorite)
    object Library : Screen("library", "Library", Icons.Default.CloudDownload)
}

@Composable
fun GlassNavigationBar(
    selectedPage: Int,
    onPageSelected: (Int) -> Unit
) {
    val items = listOf(
        Screen.Discover,
        Screen.Favorites,
        Screen.Library
    )

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
            modifier = Modifier.height(64.dp)
        ) {
            items.forEachIndexed { index, screen ->
                val selected = selectedPage == index
                
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
                    onClick = { onPageSelected(index) },
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

@Composable
fun GlassNavigationRail(
    selectedPage: Int,
    onPageSelected: (Int) -> Unit
) {
    val items = listOf(
        Screen.Discover,
        Screen.Favorites,
        Screen.Library
    )

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(80.dp)
            .padding(vertical = 16.dp, horizontal = 8.dp)
            .clip(RoundedCornerShape(32.dp))
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(32.dp)),
        color = Color.White.copy(alpha = 0.2f),
        tonalElevation = 0.dp
    ) {
        NavigationRail(
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxHeight(),
            header = {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFFE91E63),
                    modifier = Modifier.size(32.dp).padding(bottom = 16.dp)
                )
            }
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                items.forEachIndexed { index, screen ->
                    val selected = selectedPage == index
                    
                    NavigationRailItem(
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text(screen.title, style = MaterialTheme.typography.labelSmall) },
                        selected = selected,
                        onClick = { onPageSelected(index) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFFE91E63),
                            selectedTextColor = Color(0xFFE91E63),
                            unselectedIconColor = Color(0xFF424242),
                            unselectedTextColor = Color(0xFF424242),
                            indicatorColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}
