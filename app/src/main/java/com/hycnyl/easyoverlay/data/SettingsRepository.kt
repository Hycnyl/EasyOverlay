package com.hycnyl.easyoverlay.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.easySettings by preferencesDataStore("easyoverlay_settings")

enum class ThemeChoice { SYSTEM, LIGHT, DARK }
data class AppSettings(val theme: ThemeChoice = ThemeChoice.SYSTEM, val coverCutout: Boolean = true,
    val lastAutoRequested: Boolean = false, val hardwareBindings: Map<String, Set<Int>> = emptyMap())

class SettingsRepository(context: Context) {
    private val dataStore = context.easySettings
    private val theme = stringPreferencesKey("theme")
    private val cutout = booleanPreferencesKey("cover_cutout")
    private val auto = booleanPreferencesKey("auto_requested")
    val settings = dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { prefs ->
        AppSettings(runCatching { ThemeChoice.valueOf(prefs[theme] ?: "SYSTEM") }.getOrDefault(ThemeChoice.SYSTEM),
            prefs[cutout] ?: true, prefs[auto] ?: false, FUNCTIONS.associateWith { function ->
                prefs[stringSetPreferencesKey("keys_$function")].orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
            })
    }
    suspend fun setTheme(value: ThemeChoice) { dataStore.edit { it[theme] = value.name } }
    suspend fun setCoverCutout(value: Boolean) { dataStore.edit { it[cutout] = value } }
    suspend fun setAutoRequested(value: Boolean) { dataStore.edit { it[auto] = value } }
    suspend fun bind(function: String, keys: Set<Int>) {
        require(function in FUNCTIONS && keys.size <= 3) { "最多绑定三个实体键" }
        dataStore.edit { prefs ->
            FUNCTIONS.filterNot { it == function }.forEach { other ->
                val existing = prefs[stringSetPreferencesKey("keys_$other")].orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
                require(keys.isEmpty() || existing.isEmpty() || (!keys.containsAll(existing) && !existing.containsAll(keys))) { "这组按键与已有快捷键重叠，请先清除原绑定" }
            }
            prefs[stringSetPreferencesKey("keys_$function")] = keys.map { it.toString() }.toSet()
        }
    }
    companion object { val FUNCTIONS = listOf("toggle_main", "toggle_secondary", "toggle_all", "toggle_ball") }
}
