package com.example.song.data.ota

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.otaDataStore: DataStore<Preferences> by preferencesDataStore(name = "ota_update_preferences")

class OtaUpdatePreferences(private val context: Context) {

    companion object {
        private val KEY_LAST_CHECK_TIMESTAMP = longPreferencesKey("last_check_timestamp")
        private val KEY_LAST_INSTALLED_VERSION_CODE = intPreferencesKey("last_installed_version_code")
        private val KEY_REMIND_LATER_VERSION = stringPreferencesKey("remind_later_version")
        private val KEY_ACTIVE_DOWNLOAD_ID = longPreferencesKey("active_download_id")
        private val KEY_DOWNLOADED_APK_PATH = stringPreferencesKey("downloaded_apk_path")
        private val KEY_PENDING_CHANGELOG = stringPreferencesKey("pending_changelog")
    }

    val lastCheckTimestamp: Flow<Long> = context.otaDataStore.data.map { preferences ->
        preferences[KEY_LAST_CHECK_TIMESTAMP] ?: 0L
    }

    suspend fun setLastCheckTimestamp(timestamp: Long) {
        context.otaDataStore.edit { preferences ->
            preferences[KEY_LAST_CHECK_TIMESTAMP] = timestamp
        }
    }

    val lastInstalledVersionCode: Flow<Int> = context.otaDataStore.data.map { preferences ->
        preferences[KEY_LAST_INSTALLED_VERSION_CODE] ?: -1
    }

    suspend fun setLastInstalledVersionCode(code: Int) {
        context.otaDataStore.edit { preferences ->
            preferences[KEY_LAST_INSTALLED_VERSION_CODE] = code
        }
    }

    val remindLaterVersion: Flow<String?> = context.otaDataStore.data.map { preferences ->
        preferences[KEY_REMIND_LATER_VERSION]
    }

    suspend fun setRemindLaterVersion(version: String?) {
        context.otaDataStore.edit { preferences ->
            if (version == null) {
                preferences.remove(KEY_REMIND_LATER_VERSION)
            } else {
                preferences[KEY_REMIND_LATER_VERSION] = version
            }
        }
    }

    val activeDownloadId: Flow<Long> = context.otaDataStore.data.map { preferences ->
        preferences[KEY_ACTIVE_DOWNLOAD_ID] ?: -1L
    }

    suspend fun setActiveDownloadId(id: Long) {
        context.otaDataStore.edit { preferences ->
            if (id == -1L) {
                preferences.remove(KEY_ACTIVE_DOWNLOAD_ID)
            } else {
                preferences[KEY_ACTIVE_DOWNLOAD_ID] = id
            }
        }
    }

    val downloadedApkPath: Flow<String?> = context.otaDataStore.data.map { preferences ->
        preferences[KEY_DOWNLOADED_APK_PATH]
    }

    suspend fun setDownloadedApkPath(path: String?) {
        context.otaDataStore.edit { preferences ->
            if (path == null) {
                preferences.remove(KEY_DOWNLOADED_APK_PATH)
            } else {
                preferences[KEY_DOWNLOADED_APK_PATH] = path
            }
        }
    }

    val pendingChangelog: Flow<String?> = context.otaDataStore.data.map { preferences ->
        preferences[KEY_PENDING_CHANGELOG]
    }

    suspend fun setPendingChangelog(changelog: String?) {
        context.otaDataStore.edit { preferences ->
            if (changelog == null) {
                preferences.remove(KEY_PENDING_CHANGELOG)
            } else {
                preferences[KEY_PENDING_CHANGELOG] = changelog
            }
        }
    }
}
