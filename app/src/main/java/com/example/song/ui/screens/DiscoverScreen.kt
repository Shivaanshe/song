package com.example.song.ui.screens

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.example.song.SongApplication
import com.example.song.data.model.StreamingItem
import com.example.song.util.multiSelectDragHandler
import com.example.song.viewmodel.SongViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun DiscoverScreen(
    viewModel: SongViewModel,
    onSongClick: () -> Unit
) {
    val items by viewModel.topLevelStreamingItems.collectAsState()
    val isExtracting by viewModel.isExtracting.collectAsState()
    val isEngineReady by SongApplication.getInstance().isReady.collectAsState()
    val resolvingUrlId by viewModel.resolvingUrlId.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPlayingSong by viewModel.currentPlayingSong.collectAsState()
    val pendingItems by viewModel.pendingStreamingItems.collectAsState()
    val extractionError by viewModel.extractionError.collectAsState()
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddMenu by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var youtubeUrl by remember { mutableStateOf("") }
    var selectedPlaylist by rememberSaveable { mutableStateOf<StreamingItem?>(null) }
    var showAddSongDialog by remember { mutableStateOf(false) }
    val allStreamingSongs by viewModel.allStreamingSongs.collectAsState()

    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items
        else items.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    val playlists = remember(filteredItems) { filteredItems.filter { it.isPlaylist } }
    val singleSongs = remember(filteredItems) {
        filteredItems.filter { !it.isPlaylist }
    }

    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedStreamingIds by viewModel.selectedStreamingIds.collectAsState()

    val isUrlValid = remember(youtubeUrl) {
        youtubeUrl.isBlank() || (youtubeUrl.startsWith("http") && 
            (youtubeUrl.contains("youtube.com") || youtubeUrl.contains("youtu.be") || youtubeUrl.contains("spotify.com")))
    }

    val addIconRotation by animateFloatAsState(
        targetValue = if (showAddMenu) 135f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "AddIconRotation"
    )

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Auto-scroll logic handled by multiSelectDragHandler

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (selectedPlaylist != null) {
                    CenterAlignedTopAppBar(
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent
                        ),
                        title = {
                            Text(
                                selectedPlaylist?.title ?: "Playlist",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF424242)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { selectedPlaylist = null },
                                modifier = Modifier
                                    .padding(8.dp)
                                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color(0xFF424242)
                                )
                            }
                        }
                    )
                } else {
                    CenterAlignedTopAppBar(
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent
                        ),
                        title = {
                            if (isSearching) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    placeholder = { Text("Search streaming...") },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.White.copy(alpha = 0.2f),
                                        unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            isSearching = false
                                            searchQuery = ""
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close search")
                                        }
                                    }
                                )
                            } else {
                                Text(
                                    "Discover",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF424242)
                                    )
                                )
                            }
                        },
                        navigationIcon = {
                            if (!isSearching) {
                                IconButton(
                                    onClick = { isSearching = true },
                                    modifier = Modifier.background(Color.White.copy(alpha = 0.3f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF424242))
                                }
                            }
                        },
                        actions = {
                            if (!isSearching) {
                                IconButton(
                                    onClick = { if (isEngineReady) showAddMenu = !showAddMenu },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            if (isEngineReady) Color.White.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.2f),
                                            CircleShape
                                        ),
                                    enabled = isEngineReady
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Options",
                                        tint = Color(0xFF424242),
                                        modifier = Modifier
                                            .size(28.dp)
                                            .rotate(addIconRotation)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (isExtracting) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFE91E63)
                    )
                }

                if (selectedPlaylist == null) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .multiSelectDragHandler(
                                listState = listState,
                                onDragStart = { key ->
                                    if (key is Int) {
                                        viewModel.startRangeSelection(key, singleSongs.map { it.id }, isStreaming = true)
                                    }
                                },
                                onDragUpdate = { key ->
                                    if (key is Int) {
                                        viewModel.updateRangeSelection(key, singleSongs.map { it.id }, isStreaming = true)
                                    }
                                },
                                onDragEnd = {
                                    viewModel.endRangeSelection()
                                }
                            ),
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        if (playlists.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                                    Text(
                                        "Collections",
                                        modifier = Modifier.padding(horizontal = 24.dp),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF424242)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = PaddingValues(horizontal = 24.dp)
                                    ) {
                                    items(playlists, key = { it.id }) { playlist ->
                                            StreamingPlaylistCard(
                                                item = playlist,
                                                isSelected = selectedStreamingIds.contains(playlist.id),
                                                selectionMode = isSelectionMode,
                                                onClick = {
                                                    if (isSelectionMode) {
                                                        viewModel.toggleStreamingSelection(playlist.id)
                                                    } else {
                                                        selectedPlaylist = playlist
                                                    }
                                                },
                                                onLongClick = {
                                                    viewModel.toggleSelectionMode(true)
                                                    viewModel.toggleStreamingSelection(playlist.id)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (singleSongs.isNotEmpty()) {
                            item {
                                Text(
                                    "Recommended",
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF424242)
                                    )
                                )
                            }
                            items(singleSongs, key = { it.id }) { item ->
                                val isCurrentItemPlaying = currentPlayingSong?.id == (1_000_000 + item.id)
                                StreamingItemCard(
                                    item = item,
                                    enabled = isEngineReady,
                                    isResolving = resolvingUrlId == item.id,
                                    isPlaying = isPlaying && isCurrentItemPlaying,
                                    onClick = {
                                        if (isSelectionMode) {
                                            viewModel.toggleStreamingSelection(item.id)
                                        } else if (isEngineReady) {
                                            if (isCurrentItemPlaying) {
                                                viewModel.togglePlayPause()
                                            } else {
                                                viewModel.playStreamingItem(item, singleSongs)
                                                onSongClick()
                                            }
                                        }
                                    },
                                    onFavoriteToggle = {
                                        viewModel.toggleStreamingFavorite(item)
                                    },
                                    onDelete = {
                                        viewModel.deleteStreamingItem(item)
                                    },
                                    isSelected = selectedStreamingIds.contains(item.id),
                                    onLongClick = {
                                        viewModel.toggleSelectionMode(true)
                                        viewModel.toggleStreamingSelection(item.id)
                                    },
                                    selectionMode = isSelectionMode
                                )
                            }
                        } else if (playlists.isEmpty() && !isExtracting) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(top = 100.dp), contentAlignment = Alignment.Center) {
                                    Text("No items found", color = Color.Gray)
                                }
                            }
                        }
                    }
                } else {
                    val playlistItems by viewModel.getItemsForStreamingPlaylist(selectedPlaylist!!.youtubeUrl).collectAsState(initial = emptyList())
                    var localPlaylistItems by remember(playlistItems) { mutableStateOf(playlistItems.filter { !it.isPlaylist }) }
                    
                    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
                    var dragOffset by remember { mutableStateOf(0f) }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .multiSelectDragHandler(
                                listState = listState,
                                onDragStart = { key ->
                                    if (key is Int && !isSelectionMode) {
                                        // We handle reordering via card long press
                                    } else if (key is Int) {
                                        viewModel.startRangeSelection(key, localPlaylistItems.map { it.id }, isStreaming = true)
                                    }
                                },
                                onDragUpdate = { key ->
                                    if (key is Int) {
                                        viewModel.updateRangeSelection(key, localPlaylistItems.map { it.id }, isStreaming = true)
                                    }
                                },
                                onDragEnd = {
                                    viewModel.endRangeSelection()
                                }
                            ),
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        item {
                            Text(
                                "Playlist Content",
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF424242)
                                )
                            )
                        }
                        itemsIndexed(localPlaylistItems, key = { _, item -> item.id }) { index, item ->
                            val isCurrentItemPlaying = currentPlayingSong?.id == (1_000_000 + item.id)
                            val isDragging = draggedItemIndex == index
                            
                            val itemTranslationY by animateFloatAsState(
                                targetValue = if (isDragging) dragOffset else 0f,
                                label = "DragTranslation"
                            )
                            
                            val itemScale by animateFloatAsState(
                                targetValue = if (isDragging) 1.05f else 1f,
                                label = "DragScale"
                            )
                            
                            val itemZIndex = if (isDragging) 10f else 0f

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        translationY = itemTranslationY
                                        scaleX = itemScale
                                        scaleY = itemScale
                                    }
                                    .zIndex(itemZIndex)
                                    .pointerInput(localPlaylistItems) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { 
                                                if (!isSelectionMode) {
                                                    draggedItemIndex = index
                                                }
                                            },
                                            onDrag = { change, dragAmount ->
                                                if (draggedItemIndex != null) {
                                                    change.consume()
                                                    dragOffset += dragAmount.y
                                                    
                                                    // Move item logic
                                                    val targetIndex = (draggedItemIndex!! + (dragOffset / 80f).roundToInt())
                                                        .coerceIn(0, localPlaylistItems.size - 1)
                                                    
                                                    if (targetIndex != draggedItemIndex) {
                                                        val mutable = localPlaylistItems.toMutableList()
                                                        val movedItem = mutable.removeAt(draggedItemIndex!!)
                                                        mutable.add(targetIndex, movedItem)
                                                        localPlaylistItems = mutable
                                                        draggedItemIndex = targetIndex
                                                        dragOffset = 0f
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                if (draggedItemIndex != null) {
                                                    viewModel.moveStreamingItem(index, draggedItemIndex!!, localPlaylistItems)
                                                    draggedItemIndex = null
                                                    dragOffset = 0f
                                                }
                                            },
                                            onDragCancel = {
                                                draggedItemIndex = null
                                                dragOffset = 0f
                                            }
                                        )
                                    }
                            ) {
                                StreamingItemCard(
                                    item = item,
                                    enabled = isEngineReady,
                                    isResolving = resolvingUrlId == item.id,
                                    isPlaying = isPlaying && isCurrentItemPlaying,
                                    onClick = {
                                        if (isSelectionMode) {
                                            viewModel.toggleStreamingSelection(item.id)
                                        } else if (isEngineReady) {
                                            if (isCurrentItemPlaying) {
                                                viewModel.togglePlayPause()
                                            } else {
                                                viewModel.playStreamingItem(item, localPlaylistItems)
                                                onSongClick()
                                            }
                                        }
                                    },
                                    onFavoriteToggle = {
                                        viewModel.toggleStreamingFavorite(item)
                                    },
                                    onDelete = {
                                        viewModel.deleteStreamingItem(item)
                                    },
                                    isSelected = selectedStreamingIds.contains(item.id),
                                    onLongClick = {
                                        viewModel.toggleSelectionMode(true)
                                        viewModel.toggleStreamingSelection(item.id)
                                    },
                                    selectionMode = isSelectionMode
                                )
                            }
                        }
                    }
                }
            }
        }

        // Click-away layer
        if (showAddMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showAddMenu = false }
            )
        }

        // Add Options Menu (Matching Library)
        AnimatedVisibility(
            visible = showAddMenu,
            enter = fadeIn() + scaleIn(initialScale = 0.4f, transformOrigin = TransformOrigin(0.9f, 0.1f)),
            exit = fadeOut() + scaleOut(targetScale = 0.4f, transformOrigin = TransformOrigin(0.9f, 0.1f)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 70.dp, end = 16.dp)
                .zIndex(10f)
        ) {
            Box(
                modifier = Modifier
                    .width(260.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.4f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Logic Rule: Submit through this dialog should call StreamingItem saving logic
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAddMenu = false
                                showAddDialog = true
                            }
                            .clip(RoundedCornerShape(18.dp)),
                        color = Color.White.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.Black.copy(alpha = 0.05f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF424242), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Add from YouTube & Spotify",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF424242)
                                )
                            )
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!isExtracting) {
                        showAddDialog = false
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFFE91E63))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Stream from Link")
                    }
                },
                text = {
                    Column {
                        if (isExtracting) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = Color(0xFFE91E63))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Extracting...", style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            Text(
                                "Enter a YouTube or Spotify URL to add to your Discover list.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TextField(
                                value = youtubeUrl,
                                onValueChange = { youtubeUrl = it },
                                placeholder = { Text("https://youtube.com/...") },
                                singleLine = true,
                                isError = (!isUrlValid && youtubeUrl.isNotBlank()) || extractionError != null,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Black.copy(alpha = 0.05f),
                                    unfocusedContainerColor = Color.Black.copy(alpha = 0.03f),
                                    focusedIndicatorColor = Color(0xFFE91E63),
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (!isUrlValid && youtubeUrl.isNotBlank()) {
                                Text(
                                    "Invalid URL (YouTube or Spotify only)",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                                )
                            }
                            
                            extractionError?.let { error ->
                                Text(
                                    error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (!isExtracting) {
                        Button(
                            onClick = {
                                if (youtubeUrl.isNotBlank() && isUrlValid && isEngineReady) {
                                    val urlToAdd = youtubeUrl.trim()
                                    youtubeUrl = ""
                                    viewModel.fetchStreamingMetadata(urlToAdd)
                                    showAddDialog = false
                                }
                            },
                            enabled = youtubeUrl.isNotBlank() && isUrlValid && isEngineReady,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE91E63),
                                disabledContainerColor = Color(0xFFE91E63).copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (isEngineReady) "Add to Discover" else "Initializing...")
                        }
                    }
                },
                dismissButton = {
                    if (!isExtracting) {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("Close", color = Color.Gray)
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                containerColor = Color.White
            )
        }

        if (pendingItems.isNotEmpty()) {
            val playlistItem = pendingItems.find { it.isPlaylist }
            val firstTrack = pendingItems.find { !it.isPlaylist }
            
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { viewModel.clearPendingStreamingItems() },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                var isVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { isVisible = true }

                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                    exit = fadeOut(tween(300)) + scaleOut(targetScale = 0.8f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color.White.copy(alpha = 0.4f))
                            .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Immersive Header with Overlapping Images
                            Box(
                                modifier = Modifier
                                    .height(180.dp)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                // Background Image (First Track)
                                firstTrack?.thumbnailUrl?.let { url ->
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(130.dp)
                                            .rotate(-10f)
                                            .offset(x = (-30).dp)
                                            .clip(RoundedCornerShape(24.dp))
                                            .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                                            .shadow(8.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                
                                // Foreground Image (Playlist/Top)
                                (playlistItem?.thumbnailUrl ?: firstTrack?.thumbnailUrl)?.let { url ->
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(140.dp)
                                            .rotate(5f)
                                            .offset(x = 20.dp)
                                            .clip(RoundedCornerShape(24.dp))
                                            .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
                                            .shadow(16.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Import Playlist",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF333333)
                                    )
                                )
                                playlistItem?.let {
                                    Text(
                                        it.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF666666),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            Text(
                                "How would you like to add this playlist to your Discover screen?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF424242),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Action Buttons
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.addPendingStreamingItems(asCollection = true) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                    contentPadding = PaddingValues(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(Color(0xFFE040FB), Color(0xFFFF4081))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Add as Collection", fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                                
                                Surface(
                                    onClick = { viewModel.addPendingStreamingItems(asCollection = false) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    color = Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = Color(0xFF424242))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            "Add Individual Items",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF424242)
                                            )
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = { 
                                        isVisible = false
                                        scope.launch {
                                            delay(300)
                                            viewModel.clearPendingStreamingItems()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Cancel", color = Color(0xFF666666), fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selection Top Bar Overlay
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.zIndex(10f)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.85f),
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.toggleSelectionMode(false) }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color(0xFF424242))
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Text(
                        text = "${selectedStreamingIds.size} Selected",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    
                    IconButton(onClick = {
                        val count = selectedStreamingIds.size
                        viewModel.deleteSelectedItems()
                        scope.launch {
                            snackbarHostState.showSnackbar("Deleted $count items")
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color.Red)
                    }
                }
            }
        }

        // Add Song FAB for Collections (Glass style)
        if (selectedPlaylist != null && !isSelectionMode) {
            FloatingActionButton(
                onClick = { showAddSongDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .padding(bottom = 80.dp), // Adjust for navigation bar
                containerColor = Color.White.copy(alpha = 0.8f),
                contentColor = Color(0xFFE91E63),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Song to Collection")
            }
        }

        if (showAddSongDialog) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showAddSongDialog = false }) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(550.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(alpha = 0.4f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(32.dp)),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        Column {
                            Text(
                                text = "Add to Collection",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF333333),
                                    fontSize = 26.sp
                                ),
                                modifier = Modifier.padding(bottom = 20.dp)
                            )

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
                                color = Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(8.dp)
                                ) {
                                    items(allStreamingSongs) { item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    viewModel.addStreamingItemToPlaylist(item.id, selectedPlaylist?.youtubeUrl)
                                                    showAddSongDialog = false
                                                }
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = item.thumbnailUrl,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color.Black.copy(alpha = 0.05f)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column {
                                                Text(
                                                    text = item.title,
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        color = Color(0xFF424242),
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.Medium
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = item.artist ?: "YouTube Stream",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = Color(0xFF666666),
                                                        fontSize = 14.sp
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            TextButton(
                                onClick = { showAddSongDialog = false },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(
                                    "Close",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = Color(0xFFE91E63),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StreamingItemCard(
    item: StreamingItem,
    enabled: Boolean = true,
    isResolving: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDelete: () -> Unit,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
    selectionMode: Boolean = false
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.95f else if (isPlaying) pulseScale else 1f,
        animationSpec = if (isSelected || isPlaying) spring(dampingRatio = Spring.DampingRatioMediumBouncy) else tween(300),
        label = "SelectionScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .combinedClickable(
                enabled = enabled && !isResolving,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .border(
                width = if (isSelected || isPlaying) 2.dp else 1.5.dp,
                brush = when {
                    isSelected -> Brush.linearGradient(colors = listOf(Color(0xFFE040FB), Color(0xFFFF4081)))
                    isPlaying -> Brush.linearGradient(colors = listOf(Color(0xFF00E676), Color(0xFF1DE9B6)))
                    else -> Brush.linearGradient(colors = listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.3f)))
                },
                shape = RoundedCornerShape(20.dp)
            ),
        color = when {
            isSelected -> Color.White.copy(alpha = 0.4f)
            isPlaying -> Color.White.copy(alpha = 0.5f)
            else -> Color.White.copy(alpha = 0.3f)
        },
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Gray.copy(alpha = 0.2f))
            ) {
                if (item.thumbnailUrl != null) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
                if (item.isPlaylist) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White)
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                                .shadow(4.dp, CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF424242)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.artist ?: if (item.isPlaylist) "YouTube Playlist" else "YouTube Stream",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF666666)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isResolving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFE91E63)
                    )
                } else {
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (item.isFavorite) Color.Red else Color(0xFF424242),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    IconButton(
                        onClick = onClick,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.5f), CircleShape).size(32.dp),
                        enabled = enabled
                    ) {
                        Icon(
                            imageVector = when {
                                isPlaying -> Icons.Default.Pause
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color(0xFF424242),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Stream") },
            text = { Text("Are you sure you want to remove '${item.title}' from your Discover list?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StreamingPlaylistCard(
    item: StreamingItem,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "SelectionScale"
    )

    Column(
        modifier = Modifier
            .width(120.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .shadow(if (isSelected) 4.dp else 12.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFF06292), Color(0xFFBA68C8))
                    )
                )
                .border(
                    width = if (isSelected) 3.dp else 1.5.dp,
                    brush = if (isSelected) {
                        Brush.linearGradient(colors = listOf(Color(0xFFE040FB), Color(0xFFFF4081)))
                    } else {
                        Brush.linearGradient(colors = listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.3f)))
                    },
                    shape = RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!item.thumbnailUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = Color.White.copy(alpha = 0.5f)
                )
            }

            // Overlay a small playlist icon in the corner
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                            .shadow(8.dp, CircleShape)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF424242)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "YouTube Playlist",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF666666)
        )
    }
}
