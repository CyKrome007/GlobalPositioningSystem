package com.CyKrome007.GlobalPositioningSystem

import android.content.Context
import android.widget.Toast
import org.osmdroid.bonuspack.location.GeocoderNominatim
import org.osmdroid.util.GeoPoint
import java.io.DataOutputStream

class FavoritesManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("Favorites", Context.MODE_PRIVATE)

    fun getFavorites(): List<Pair<String, GeoPoint>> {
        return prefs.all.mapNotNull { (key, value) ->
            val parts = (value as String).split(",")
            if (parts.size == 2) {
                key to GeoPoint(parts[0].toDouble(), parts[1].toDouble())
            } else {
                null
            }
        }
    }

    fun addFavorite(name: String, location: GeoPoint) {
        prefs.edit().putString(name, "${location.latitude},${location.longitude}").apply()
    }
    
    fun removeFavorite(name: String) {
        prefs.edit().remove(name).apply()
    }
}

fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/**
 * Unified logging function that tries to use XposedBridge.log() if available,
 * otherwise falls back to android.util.Log
 */
fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
    try {
        // Try to use XposedBridge if available (only works in Xposed module context)
        val xposedBridgeClass = Class.forName("de.robv.android.xposed.XposedBridge")
        val logMethod = xposedBridgeClass.getMethod("log", String::class.java)
        logMethod.invoke(null, "[$level] $tag: $message${if (throwable != null) "\n${throwable.stackTraceToString()}" else ""}")
    } catch (e: ClassNotFoundException) {
        // XposedBridge not available, use android.util.Log
        when (level) {
            "D" -> android.util.Log.d(tag, message, throwable)
            "E" -> android.util.Log.e(tag, message, throwable)
            "W" -> android.util.Log.w(tag, message, throwable)
            "I" -> android.util.Log.i(tag, message, throwable)
            else -> android.util.Log.d(tag, message, throwable)
        }
    } catch (e: Exception) {
        // Fallback to android.util.Log if XposedBridge call fails
        android.util.Log.d(tag, message, throwable)
    }
}

fun logD(tag: String, message: String) = log("D", tag, message)
fun logE(tag: String, message: String, throwable: Throwable? = null) = log("E", tag, message, throwable)
fun logW(tag: String, message: String) = log("W", tag, message)
fun logI(tag: String, message: String) = log("I", tag, message)

/**
 * Check if device has root access
 */
fun isRootAvailable(): Boolean {
    return try {
        val process = Runtime.getRuntime().exec("su")
        val os = DataOutputStream(process.outputStream)
        os.writeBytes("exit\n")
        os.flush()
        val exitValue = process.waitFor()
        os.close()
        exitValue == 0
    } catch (e: Exception) {
        false
    }
}

/**
 * Execute a command with root access
 * @param command The command to execute
 * @return Pair of (success: Boolean, output: String)
 */
fun executeRootCommand(command: String): Pair<Boolean, String> {
    return try {
        val process = Runtime.getRuntime().exec("su")
        val os = DataOutputStream(process.outputStream)
        val output = StringBuilder()
        
        // Write command
        os.writeBytes("$command\n")
        os.writeBytes("exit\n")
        os.flush()
        os.close()
        
        // Read output
        val reader = process.inputStream.bufferedReader()
        reader.useLines { lines ->
            lines.forEach { output.append(it).append("\n") }
        }
        
        val exitValue = process.waitFor()
        val success = exitValue == 0
        
        Pair(success, output.toString().trim())
    } catch (e: Exception) {
        logE("GPS", "Root command execution failed: ${e.message}", e)
        Pair(false, e.message ?: "Unknown error")
    }
}

/**
 * Set file permissions using root access
 * @param filePath Path to the file
 * @param permissions Permission string (e.g., "644")
 * @return true if successful, false otherwise
 */
fun setFilePermissionsWithRoot(filePath: String, permissions: String = "644"): Boolean {
    if (!isRootAvailable()) {
        logD("GPS", "Root not available, skipping chmod")
        return false
    }
    
    val (success, output) = executeRootCommand("chmod $permissions \"$filePath\"")
    if (success) {
        logD("GPS", "Successfully set permissions $permissions on $filePath")
    } else {
        logW("GPS", "Failed to set permissions: $output")
    }
    return success
}
