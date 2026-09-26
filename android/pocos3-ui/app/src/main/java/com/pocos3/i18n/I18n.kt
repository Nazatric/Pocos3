package com.pocos3.i18n

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.Locale

// =============================================================================
// i18n strings loader.
//
// Loads strings from assets/i18n/<lang>.json. Falls back to the default
// English strings.xml resource bundle. Used by the Compose UI so screens
// can pull localised strings without going through Resource ID lookups.
// =============================================================================

class I18n private constructor(private val context: Context) {

    private val _strings = MutableStateFlow<Map<String, String>>(emptyMap())
    val strings: StateFlow<Map<String, String>> = _strings.asStateFlow()

    private val _currentLanguage = MutableStateFlow("en")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    init {
        runBlocking { load(Locale.getDefault().language.ifEmpty { "en" }) }
    }

    suspend fun load(language: String) = withContext(Dispatchers.IO) {
        val asset = "i18n/$language.json"
        val map = mutableMapOf<String, String>()
        runCatching {
            context.assets.open(asset).use { input ->
                val text = input.bufferedReader().readText()
                val json = JSONObject(text)
                for (key in json.keys()) {
                    map[key] = json.getString(key)
                }
            }
        }
        _strings.value = map
        _currentLanguage.value = language
    }

    fun get(key: String, default: String = key): String {
        return _strings.value[key] ?: default
    }

    companion object {
        @Volatile private var instance: I18n? = null
        fun get(context: Context): I18n =
            instance ?: synchronized(this) {
                instance ?: I18n(context.applicationContext).also { instance = it }
            }
    }
}
