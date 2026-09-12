package com.example.song.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.song.MainActivity
import com.example.song.data.ota.OtaUpdateManager
import com.example.song.data.ota.UpdateCheckResult
import com.example.song.ui.theme.SongTheme
import kotlinx.coroutines.launch
import java.io.File

class RecoveryActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CRASH_LOG = "EXTRA_CRASH_LOG"
        private const val TAG = "RecoveryActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val crashLog = intent.getStringExtra(EXTRA_CRASH_LOG) ?: "No stack trace available."

        setContent {
            SongTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    RecoveryScreen(
                        crashLog = crashLog,
                        onRestartApp = { restartApp() },
                        onOpenBrowser = { openBrowserReleases() }
                    )
                }
            }
        }
    }

    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    private fun openBrowserReleases() {
        try {
            val url = "https://github.com/shiva/song/releases"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to open browser", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun RecoveryScreen(
    crashLog: String,
    onRestartApp: () -> Unit,
    onOpenBrowser: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val otaManager = remember { OtaUpdateManager(context) }

    var isCheckingOta by remember { mutableStateOf(false) }
    var otaStatusMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFD32F2F).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "Crash Icon",
                    tint = Color(0xFFEF5350),
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Safe Mode & Recovery",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = "Song encountered a critical error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Crash Log Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Crash Diagnostics",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFEF5350)
                    )

                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Crash Log", crashLog)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Crash log copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Log",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = crashLog,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = Color(0xFFFF8A80),
                        modifier = Modifier.verticalScroll(scrollState)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        otaStatusMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Actions Grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Emergency Hotfix Check
            Button(
                onClick = {
                    scope.launch {
                        isCheckingOta = true
                        otaStatusMessage = "Checking GitHub for emergency hotfix..."
                        try {
                            val result = otaManager.checkForUpdate(force = true)
                            when (result) {
                                is UpdateCheckResult.UpdateAvailable -> {
                                    otaStatusMessage = "Hotfix found (${result.release.tagName}). Downloading..."
                                    val downloadId = otaManager.startDownload(result.release, result.apkAsset)
                                    otaStatusMessage = "Downloading hotfix (ID $downloadId). Check notifications."
                                }
                                is UpdateCheckResult.UpToDate -> {
                                    otaStatusMessage = "No newer hotfix available on GitHub."
                                }
                                is UpdateCheckResult.Error -> {
                                    otaStatusMessage = "Check failed: ${result.message}"
                                }
                                UpdateCheckResult.Throttled -> {
                                    otaStatusMessage = "Checked recently."
                                }
                            }
                        } catch (e: Exception) {
                            otaStatusMessage = "Error checking hotfix: ${e.message}"
                        } finally {
                            isCheckingOta = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCheckingOta,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                if (isCheckingOta) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check for Emergency Hotfix")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Reset App Data
                OutlinedButton(
                    onClick = {
                        try {
                            context.deleteDatabase("song_database")
                            context.deleteDatabase("pulse_music_database")
                            context.cacheDir.deleteRecursively()
                            Toast.makeText(context, "App database & cache cleared", Toast.LENGTH_LONG).show()
                            otaStatusMessage = "Data reset complete. Try restarting app."
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error resetting data: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350))
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset Data", fontSize = 12.sp)
                }

                // Open Browser
                OutlinedButton(
                    onClick = onOpenBrowser,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Releases", fontSize = 12.sp)
                }
            }

            // Restart App
            Button(
                onClick = onRestartApp,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restart App")
            }
        }
    }
}
