package com.example.song.data.ota

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.song.BuildConfig
import com.example.song.data.api.GitHubAsset
import com.example.song.data.api.GitHubRelease
import com.example.song.data.api.GitHubReleaseService
import com.example.song.util.SemVer
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

sealed class UpdateCheckResult {
    data class UpdateAvailable(val release: GitHubRelease, val apkAsset: GitHubAsset) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
    object Throttled : UpdateCheckResult()
}

data class DownloadProgress(
    val downloadId: Long,
    val status: Int, // DownloadManager.STATUS_*
    val bytesDownloaded: Long,
    val totalBytes: Long
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes.toFloat() else 0f
}

data class PostUpdateResult(
    val isPostUpdate: Boolean,
    val changelog: String?
)

class OtaUpdateManager(
    private val context: Context,
    private val preferences: OtaUpdatePreferences = OtaUpdatePreferences(context),
    private val owner: String = DEFAULT_OWNER,
    private val repo: String = DEFAULT_REPO
) {

    companion object {
        private const val TAG = "OtaUpdateManager"
        const val DEFAULT_OWNER = "Shivaanshe"
        const val DEFAULT_REPO = "song"
        const val AUTO_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private val retrofitService: GitHubReleaseService by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubReleaseService::class.java)
    }

    private val downloadManager: DownloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    /**
     * Checks GitHub for the latest release.
     * @param force If true, bypasses the 8-hour rate limiting cache check.
     */
    suspend fun checkForUpdate(
        force: Boolean = false,
        currentVersionName: String = BuildConfig.VERSION_NAME
    ): UpdateCheckResult {
        val now = System.currentTimeMillis()
        if (!force) {
            val lastCheck = preferences.lastCheckTimestamp.first()
            if (now - lastCheck < AUTO_CHECK_INTERVAL_MS) {
                Log.d(TAG, "Update check throttled. Last check: $lastCheck")
                return UpdateCheckResult.Throttled
            }
        }

        return try {
            Log.d(TAG, "Fetching latest release from GitHub for $owner/$repo...")
            val latestRelease = retrofitService.getLatestRelease(owner, repo)
            preferences.setLastCheckTimestamp(now)

            val apkAsset = latestRelease.apkAsset
            if (apkAsset == null) {
                Log.w(TAG, "Latest release ${latestRelease.tagName} has no valid APK asset")
                return UpdateCheckResult.Error("No APK file found in latest release assets.")
            }

            val currentSemVer = SemVer.parse(currentVersionName) ?: SemVer(0, 0, 0)
            val latestSemVer = SemVer.parse(latestRelease.tagName) ?: SemVer(0, 0, 0)

            Log.d(TAG, "Current version: $currentSemVer, Latest release: $latestSemVer")

            if (latestSemVer.isNewerThan(currentSemVer)) {
                // Check if user chose "Remind Later" / deferred update (only if auto-check)
                if (!force) {
                    val shouldPrompt = preferences.shouldPromptUpdate(latestRelease.tagName)
                    if (!shouldPrompt) {
                        Log.d(TAG, "User opted to remind later / deferred version ${latestRelease.tagName}")
                        return UpdateCheckResult.Throttled
                    }
                }

                // Save pending changelog for post-update onboarding
                preferences.setPendingChangelog(latestRelease.body)
                UpdateCheckResult.UpdateAvailable(latestRelease, apkAsset)
            } else {
                UpdateCheckResult.UpToDate(currentVersionName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates", e)
            UpdateCheckResult.Error(e.message ?: "Failed to check for updates")
        }
    }

    /**
     * Enqueues the APK asset download via Android DownloadManager.
     */
    suspend fun startDownload(release: GitHubRelease, asset: GitHubAsset): Long {
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("External downloads directory unavailable")

        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val fileName = asset.name.ifBlank { "song-release-${release.tagName}.apk" }
        val targetFile = File(downloadsDir, fileName)
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(asset.downloadUrl))
            .setTitle("Downloading Song ${release.tagName}")
            .setDescription("Downloading app update...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)

        val downloadId = downloadManager.enqueue(request)
        preferences.setActiveDownloadId(downloadId)
        preferences.setDownloadedApkPath(targetFile.absolutePath)
        Log.d(TAG, "Enqueued download ID: $downloadId to path: ${targetFile.absolutePath}")
        return downloadId
    }

    /**
     * Queries current status and progress of an active download.
     */
    fun queryDownloadProgress(downloadId: Long): DownloadProgress? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query) ?: return null

        cursor.use {
            if (it.moveToFirst()) {
                val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val downloadedIndex = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIndex = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                val status = if (statusIndex != -1) it.getInt(statusIndex) else DownloadManager.STATUS_FAILED
                val downloaded = if (downloadedIndex != -1) it.getLong(downloadedIndex) else 0L
                val total = if (totalIndex != -1) it.getLong(totalIndex) else 0L

                return DownloadProgress(downloadId, status, downloaded, total)
            }
        }
        return null
    }

    /**
     * Checks if the app has permission to request package installation (`REQUEST_INSTALL_PACKAGES`).
     */
    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Creates intent to open System Settings for enabling "Install Unknown Apps".
     */
    fun getInstallUnknownAppsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Dispatches the downloaded APK to the system package installer via FileProvider.
     */
    fun installApk(apkFile: File): Boolean {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            Log.e(TAG, "APK file does not exist or is empty: ${apkFile.absolutePath}")
            return false
        }

        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            Log.d(TAG, "Launching package installer intent for $apkUri")
            context.startActivity(installIntent)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            return false
        }
    }

    /**
     * Post-update housekeeping: compares current [BuildConfig.VERSION_CODE] against DataStore.
     * If incremented: renders "What's New" changelog, resets pending download preferences,
     * and deletes cached installation package from external storage.
     */
    suspend fun checkPostUpdateHousekeeping(currentVersionCode: Int = BuildConfig.VERSION_CODE): PostUpdateResult {
        val lastInstalledCode = preferences.lastInstalledVersionCode.first()

        if (lastInstalledCode == -1) {
            // First run on this installation
            preferences.setLastInstalledVersionCode(currentVersionCode)
            cleanDownloadedApks()
            return PostUpdateResult(isPostUpdate = false, changelog = null)
        }

        if (currentVersionCode > lastInstalledCode) {
            Log.d(TAG, "App updated from versionCode $lastInstalledCode to $currentVersionCode")
            val changelog = preferences.pendingChangelog.first()

            // Update stored version code and clean up residual APKs
            preferences.setLastInstalledVersionCode(currentVersionCode)
            preferences.setActiveDownloadId(-1L)
            preferences.setDownloadedApkPath(null)
            preferences.clearRemindLater()
            cleanDownloadedApks()

            return PostUpdateResult(isPostUpdate = true, changelog = changelog)
        }

        return PostUpdateResult(isPostUpdate = false, changelog = null)
    }

    /**
     * Deletes all cached .apk files in the app's external downloads directory.
     */
    fun cleanDownloadedApks() {
        try {
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir?.listFiles()?.forEach { file ->
                if (file.name.endsWith(".apk", ignoreCase = true)) {
                    val deleted = file.delete()
                    Log.d(TAG, "Cleaning up cached APK ${file.name}: $deleted")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning downloaded APKs", e)
        }
    }

    suspend fun setRemindLater(versionTag: String, millisFromNow: Long = 86_400_000L) {
        preferences.setRemindLater(millisFromNow, versionTag)
    }

    suspend fun clearPendingChangelog() {
        preferences.setPendingChangelog(null)
    }
}
