package com.maratonTv.service.manager

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class ChromeExtension(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val matches: List<String>,
    val jsCode: String
)

object ChromeExtensionManager {
    private const val PREFS_NAME = "chrome_extensions_prefs"
    private const val KEY_EXTENSIONS = "extensions_list_json"

    // Default built-in Blooderscrap Chrome Extension
    private val defaultBlooderscrap = ChromeExtension(
        id = "blooderscrap_default",
        name = "Blooderscrap",
        version = "2.1.0",
        description = "Extensión predeterminada que automatiza la extracción de transmisiones y bypass de anuncios en reproductores.",
        isEnabled = true,
        isBuiltIn = true,
        matches = listOf("*://www3.seriesmetro.net/*", "*://seriesmetro.net/*"),
        jsCode = """
            // Blooderscrap Chrome Extension Content Script
            console.log("[Chrome Extension] Blooderscrap inicializado.");
            
            // Auto bypass redirects or overlays
            if (window.location.href.includes("trembed")) {
                console.log("[Chrome Extension] Automatizando reproductor incrustado.");
                // Remove ad block overlays
                const overlays = document.querySelectorAll('div[style*="z-index"][style*="position: fixed"]');
                overlays.forEach(el => el.remove());
            }
            
            // Listener for general page queries
            window.chrome = window.chrome || {};
            window.chrome.runtime = window.chrome.runtime || {};
            window.chrome.runtime.onMessage = window.chrome.runtime.onMessage || {
                listeners: [],
                addListener: function(callback) {
                    this.listeners.push(callback);
                }
            };
        """.trimIndent()
    )

    fun getExtensions(context: Context): List<ChromeExtension> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_EXTENSIONS, null)
        if (jsonStr.isNullOrEmpty()) {
            // First run, populate default
            val defaultList = listOf(defaultBlooderscrap)
            saveExtensions(context, defaultList)
            return defaultList
        }

        val list = mutableListOf<ChromeExtension>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val matchesArray = obj.optJSONArray("matches") ?: JSONArray()
                val matchesList = mutableListOf<String>()
                for (j in 0 until matchesArray.length()) {
                    matchesList.add(matchesArray.getString(j))
                }
                list.add(
                    ChromeExtension(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        version = obj.getString("version"),
                        description = obj.getString("description"),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        isBuiltIn = obj.optBoolean("isBuiltIn", false),
                        matches = matchesList,
                        jsCode = obj.getString("jsCode")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("ChromeExtensionManager", "Error parsing extensions: ${e.message}")
            return listOf(defaultBlooderscrap)
        }

        // Always ensure Blooderscrap exists or is restored if built-in
        if (!list.any { it.id == "blooderscrap_default" }) {
            list.add(0, defaultBlooderscrap)
        }
        return list
    }

    fun saveExtensions(context: Context, list: List<ChromeExtension>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val array = JSONArray()
            list.forEach { ext ->
                val obj = JSONObject().apply {
                    put("id", ext.id)
                    put("name", ext.name)
                    put("version", ext.version)
                    put("description", ext.description)
                    put("isEnabled", ext.isEnabled)
                    put("isBuiltIn", ext.isBuiltIn)
                    put("jsCode", ext.jsCode)
                    
                    val matchesArray = JSONArray()
                    ext.matches.forEach { matchesArray.put(it) }
                    put("matches", matchesArray)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_EXTENSIONS, array.toString()).apply()
        } catch (e: Exception) {
            Log.e("ChromeExtensionManager", "Error saving extensions: ${e.message}")
        }
    }

    fun addExtension(context: Context, extension: ChromeExtension) {
        val current = getExtensions(context).toMutableList()
        current.removeAll { it.id == extension.id }
        current.add(extension)
        saveExtensions(context, current)
    }

    fun removeExtension(context: Context, id: String) {
        val current = getExtensions(context).filter { it.id != id || it.isBuiltIn } // Cannot delete built-in Blooderscrap
        saveExtensions(context, current)
    }

    fun toggleExtension(context: Context, id: String) {
        val current = getExtensions(context).map {
            if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it
        }
        saveExtensions(context, current)
    }
}
