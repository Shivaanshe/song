package com.example.song.viewmodel

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.song.data.api.GitHubAsset
import com.example.song.data.api.GitHubRelease
import com.example.song.data.ota.DownloadProgress
import com.example.song.data.ota.OtaUpdateManager
import com.example.song.data.ota.UpdateCheckResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class OtaUpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "OtaUpdateViewModel"
    private val otaManager = OtaUpdateManager(application)

    private val _isChecking = MutableStateFlow(false)
    val isChecking = _isChecking.asStateFlow()

    private val _updateCheckResult = MutableStateFlow<UpdateCheckResult?>(null)
    val updateCheckResult = _updateCheckResult.asStateFlow()

    private val _activeRelease = MutableStateFlow<GitHubRelease?>(null)
    val activeRelease = _activeRelease.asStateFlow()

    private val _activeAsset = MutableStateFlow<GitHubAsset?>(null)
    val activeAsset = _activeAsset.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _showUpdateModal = MutableStateFlow(false)
    val showUpdateModal = _showUpdateModal.asStateFlow()

    private val _showWhatsNewModal = MutableStateFlow(false)
    val showWhatsNewModal = _showWhatsNewModal.asStateFlow()

    private val _whatsNewChangelog = MutableStateFlow<String?>(null)
    val whatsNewChangelog = _whatsNewChangelog.asStateFlow()

    private val _showPermissionModal = MutableStateFlow(false)
    val showPermissionModal = _showPermissionModal.asStateFlow()

    private val _showSettingsModal = MutableStateFlow(false)
    val showSettingsModal = _showSettingsModal.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage = _toastMessage.asStateFlow()

    private var progressPollingJob: Job? = null
    private var downloadedFile: File? = null

    /**
     * Called on application start.
     * First checks post-update housekeeping (cleaning APKs, showing "What's New" if version updated),
     * then executes auto-throttled update check.
     */
    fun initializeOnStart() {
        viewModelScope.launch {
            try {
                val postUpdate = otaManager.checkPostUpdateHousekeeping()
                if (postUpdate.isPostUpdate) {
                    _whatsNewChangelog.value = postUpdate.changelog
                    _showWhatsNewModal.value = true
                }

                // Cold start background check
                checkForUpdates(force = false)
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing OTA check", e)
            }
        }
    }

    fun checkForUpdates(force: Boolean = false) {
        viewModelScope.launch {
            _isChecking.value = true
            try {
                val result = otaManager.checkForUpdate(force = force)
                _updateCheckResult.value = result

                when (result) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        _activeRelease.value = result.release
                        _activeAsset.value = result.apkAsset
                        _showUpdateModal.value = true
                    }
                    is UpdateCheckResult.UpToDate -> {
                        if (force) {
                            _toastMessage.value = "App is up to date (v${result.currentVersion})"
                        }
                    }
                    is UpdateCheckResult.Error -> {
                        if (force) {
                            _toastMessage.value = "Update check failed: ${result.message}"
                        }
                    }
                    UpdateCheckResult.Throttled -> {
                        if (force) {
                            _toastMessage.value = "Checked recently. Up to date."
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for updates", e)
                if (force) {
                    _toastMessage.value = "Error checking for updates: ${e.message}"
                }
            } finally {
                _isChecking.value = false
            }
        }
    }

    fun startDownload() {
        val release = _activeRelease.value ?: return
        val asset = _activeAsset.value ?: return

        viewModelScope.launch {
            try {
                _isDownloading.value = true
                val downloadId = otaManager.startDownload(release, asset)
                startProgressPolling(downloadId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start download", e)
                _isDownloading.value = false
                _toastMessage.value = "Download failed: ${e.message}"
            }
        }
    }

    private fun startProgressPolling(downloadId: Long) {
        progressPollingJob?.cancel()
        progressPollingJob = viewModelScope.launch {
            while (_isDownloading.value) {
                val progress = otaManager.queryDownloadProgress(downloadId)
                _downloadProgress.value = progress

                if (progress != null) {
                    when (progress.status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            _isDownloading.value = false
                            onDownloadCompleted(downloadId)
                            break
                        }
                        DownloadManager.STATUS_FAILED -> {
                            _isDownloading.value = false
                            _toastMessage.value = "Download failed via DownloadManager"
                            break
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    fun onDownloadCompleted(downloadId: Long) {
        viewModelScope.launch {
            _isDownloading.value = false
            progressPollingJob?.cancel()

            val downloadsDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val files = downloadsDir?.listFiles()
            val apkFile = files?.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

            if (apkFile != null && apkFile.exists()) {
                downloadedFile = apkFile
                triggerInstallFlow(apkFile)
            } else {
                _toastMessage.value = "Downloaded APK not found"
            }
        }
    }

    fun triggerInstallFlow(apkFile: File? = downloadedFile) {
        val targetFile = apkFile ?: downloadedFile
        if (targetFile == null || !targetFile.exists()) {
            _toastMessage.value = "APK file not found for installation"
            return
        }

        if (otaManager.canInstallPackages()) {
            val launched = otaManager.installApk(targetFile)
            if (!launched) {
                _toastMessage.value = "Failed to launch package installer"
            }
        } else {
            // Prompt user to grant "Install Unknown Apps" permission
            _showPermissionModal.value = true
        }
    }

    fun openPermissionSettings(context: Context) {
        val intent = otaManager.getInstallUnknownAppsIntent()
        context.startActivity(intent)
        _showPermissionModal.value = false
    }

    fun remindLater(millisFromNow: Long = 86_400_000L) {
        val release = _activeRelease.value
        if (release != null) {
            viewModelScope.launch {
                otaManager.setRemindLater(release.tagName, millisFromNow)
                _showUpdateModal.value = false
            }
        } else {
            _showUpdateModal.value = false
        }
    }

    fun dismissUpdateModal() {
        _showUpdateModal.value = false
    }

    fun dismissWhatsNewModal() {
        viewModelScope.launch {
            otaManager.clearPendingChangelog()
            _showWhatsNewModal.value = false
        }
    }

    fun dismissPermissionModal() {
        _showPermissionModal.value = false
    }

    fun openSettingsModal() {
        _showSettingsModal.value = true
    }

    fun dismissSettingsModal() {
        _showSettingsModal.value = false
    }

    fun clearToast() {
        _toastMessage.value = null
    }
}
