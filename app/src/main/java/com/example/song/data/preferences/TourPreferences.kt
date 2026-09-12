package com.example.song.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.tourDataStore: DataStore<Preferences> by preferencesDataStore(name = "tour_preferences")

class TourPreferences(private val context: Context) {

    companion object {
        private val KEY_HAS_COMPLETED_TOUR = booleanPreferencesKey("has_completed_interactive_tour")
    }

    val hasCompletedTour: Flow<Boolean> = context.tourDataStore.data.map { preferences ->
        preferences[KEY_HAS_COMPLETED_TOUR] ?: false
    }

    suspend fun setTourCompleted(completed: Boolean = true) {
        context.tourDataStore.edit { preferences ->
            preferences[KEY_HAS_COMPLETED_TOUR] = completed
        }
    }
}
