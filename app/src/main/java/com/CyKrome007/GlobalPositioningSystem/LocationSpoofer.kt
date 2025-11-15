package com.CyKrome007.GlobalPositioningSystem

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import android.util.Log

class LocationSpoofer : IXposedHookLoadPackage {

    private val prefs = XSharedPreferences("com.CyKrome007.GlobalPositioningSystem", "LocationSpoofer")
    
    /**
     * Log to both XposedBridge and Android Log for unified logging
     */
    private fun logD(tag: String, message: String) {
        try {
            XposedBridge.log("[$tag] $message")
            android.util.Log.d(tag, message)
        } catch (e: Exception) {
            // Fallback to just XposedBridge if Log fails
            try {
                XposedBridge.log("[$tag] $message")
            } catch (e2: Exception) {
                // Both failed, ignore
            }
        }
    }
    
    private fun logE(tag: String, message: String, throwable: Throwable? = null) {
        try {
            XposedBridge.log("[$tag] $message${if (throwable != null) "\n${throwable.stackTraceToString()}" else ""}")
            android.util.Log.e(tag, message, throwable)
        } catch (e: Exception) {
            try {
                XposedBridge.log("[$tag] $message")
            } catch (e2: Exception) {
                // Both failed, ignore
            }
        }
    }
    
    private fun logW(tag: String, message: String) {
        try {
            XposedBridge.log("[$tag] $message")
            android.util.Log.w(tag, message)
        } catch (e: Exception) {
            try {
                XposedBridge.log("[$tag] $message")
            } catch (e2: Exception) {
                // Both failed, ignore
            }
        }
    }
    
    init {
        // Log when module is first loaded
        XposedBridge.log("GPS Spoofer: Module initialized!")
        logD("GPS", "GPS Spoofer: Module initialized!")
    }
    
    private fun getSpoofedLatitude(): Double? {
        try {
            prefs.reload()
            val latStr = prefs.getString("latitude", null)
            if (latStr != null) {
                val lat = latStr.toDoubleOrNull()
                return if (lat != null && lat != 0.0) lat else null
            } else {
                // Fallback: Try direct file read
                return getLatitudeFromFileDirectly()
            }
        } catch (e: Exception) {
            XposedBridge.log("GPS Spoofer: Error reading latitude: ${e.message}, ${e.javaClass.name}")
            logD("GPS", "GPS Spoofer: Error reading latitude: ${e.message}, ${e.javaClass.name}")
            return getLatitudeFromFileDirectly()
        }
    }
    
    private fun getLatitudeFromFileDirectly(): Double? {
        try {
            val prefsFile = java.io.File("/data/data/com.CyKrome007.GlobalPositioningSystem/shared_prefs/LocationSpoofer.xml")
            if (prefsFile.exists() && prefsFile.canRead()) {
                val content = prefsFile.readText()
                val latMatch = Regex("name=\"latitude\">([^<]+)</string>").find(content)
                if (latMatch != null) {
                    val latStr = latMatch.groupValues[1]
                    val lat = latStr.toDoubleOrNull()
                    return if (lat != null && lat != 0.0) lat else null
                }
            }
        } catch (e: Exception) {
            // Silent fail - already logged in isSpoofingEnabled
        }
        return null
    }
    
    private fun getSpoofedLongitude(): Double? {
        try {
            prefs.reload()
            val lonStr = prefs.getString("longitude", null)
            if (lonStr != null) {
                val lon = lonStr.toDoubleOrNull()
                return if (lon != null && lon != 0.0) lon else null
            } else {
                // Fallback: Try direct file read
                return getLongitudeFromFileDirectly()
            }
        } catch (e: Exception) {
            XposedBridge.log("GPS Spoofer: Error reading longitude: ${e.message}")
            logD("GPS", "GPS Spoofer: Error reading longitude: ${e.message}")
            return getLongitudeFromFileDirectly()
        }
    }
    
    private fun getLongitudeFromFileDirectly(): Double? {
        try {
            val prefsFile = java.io.File("/data/data/com.CyKrome007.GlobalPositioningSystem/shared_prefs/LocationSpoofer.xml")
            if (prefsFile.exists() && prefsFile.canRead()) {
                val content = prefsFile.readText()
                val lonMatch = Regex("name=\"longitude\">([^<]+)</string>").find(content)
                if (lonMatch != null) {
                    val lonStr = lonMatch.groupValues[1]
                    val lon = lonStr.toDoubleOrNull()
                    return if (lon != null && lon != 0.0) lon else null
                }
            }
        } catch (e: Exception) {
            // Silent fail - already logged in isSpoofingEnabled
        }
        return null
    }
    
    private fun isSpoofingEnabled(): Boolean {
        try {
            prefs.reload()
            val allKeys = prefs.all
            XposedBridge.log("GPS Spoofer: XSharedPreferences.all keys: ${allKeys.keys}, size: ${allKeys.size}")
            logD("GPS", "GPS Spoofer: XSharedPreferences.all keys: ${allKeys.keys}, size: ${allKeys.size}")
            
            // If XSharedPreferences returns empty, try reading file directly
            if (allKeys.isEmpty()) {
                XposedBridge.log("GPS Spoofer: XSharedPreferences is empty, trying direct file read...")
                logD("GPS", "GPS Spoofer: XSharedPreferences is empty, trying direct file read...")
                return readPreferencesFromFileDirectly()
            }
            
            val enabled = prefs.getBoolean("spoofing_enabled", false)
            // Log state changes
            if (enabled) {
                val lat = getSpoofedLatitude()
                val lon = getSpoofedLongitude()
                XposedBridge.log("GPS Spoofer: Spoofing ENABLED - Lat=$lat, Lon=$lon")
                logD("GPS", "GPS Spoofer: Spoofing ENABLED - Lat=$lat, Lon=$lon")
            } else {
                XposedBridge.log("GPS Spoofer: Spoofing DISABLED")
                logD("GPS", "GPS Spoofer: Spoofing DISABLED")
            }
            return enabled
        } catch (e: Exception) {
            XposedBridge.log("GPS Spoofer: Error reading spoofing_enabled: ${e.message}, ${e.javaClass.name}")
            logD("GPS", "GPS Spoofer: Error reading spoofing_enabled: ${e.message}, ${e.javaClass.name}")
            // Try direct file read as fallback
            return readPreferencesFromFileDirectly()
        }
    }
    
    /**
     * Fallback: Read preferences file directly if XSharedPreferences fails
     */
    private fun readPreferencesFromFileDirectly(): Boolean {
        try {
            val prefsFile = java.io.File("/data/data/com.CyKrome007.GlobalPositioningSystem/shared_prefs/LocationSpoofer.xml")
            val prefsDir = java.io.File("/data/data/com.CyKrome007.GlobalPositioningSystem/shared_prefs/")
            
            XposedBridge.log("GPS Spoofer: Direct file read - exists=${prefsFile.exists()}, readable=${prefsFile.canRead()}, path=${prefsFile.absolutePath}")
            logD("GPS", "GPS Spoofer: Direct file read - exists=${prefsFile.exists()}, readable=${prefsFile.canRead()}, path=${prefsFile.absolutePath}")
            
            // Check if directory exists and list files
            if (prefsDir.exists()) {
                try {
                    val files = prefsDir.listFiles()
                    val fileNames = files?.map { it.name } ?: emptyList()
                    XposedBridge.log("GPS Spoofer: shared_prefs directory exists, files: $fileNames")
                    logD("GPS", "GPS Spoofer: shared_prefs directory exists, files: $fileNames")
                } catch (e: Exception) {
                    XposedBridge.log("GPS Spoofer: Cannot list shared_prefs directory: ${e.message}")
                    logD("GPS", "GPS Spoofer: Cannot list shared_prefs directory: ${e.message}")
                }
            } else {
                XposedBridge.log("GPS Spoofer: shared_prefs directory does not exist: ${prefsDir.absolutePath}")
                logD("GPS", "GPS Spoofer: shared_prefs directory does not exist: ${prefsDir.absolutePath}")
            }
            
            if (!prefsFile.exists()) {
                XposedBridge.log("GPS Spoofer: Preferences file does not exist - user may not have started spoofing yet")
                logD("GPS", "GPS Spoofer: Preferences file does not exist - user may not have started spoofing yet")
                return false
            }
            
            if (!prefsFile.canRead()) {
                XposedBridge.log("GPS Spoofer: Preferences file exists but is not readable - trying to read anyway")
                logD("GPS", "GPS Spoofer: Preferences file exists but is not readable - trying to read anyway")
                // Try to read anyway - might work from system context
            }
            
            val content = prefsFile.readText()
            XposedBridge.log("GPS Spoofer: Direct file content (first 200 chars): ${content.take(200)}")
            logD("GPS", "GPS Spoofer: Direct file content (first 200 chars): ${content.take(200)}")
            
            // Parse XML manually to extract spoofing_enabled
            val enabledMatch = Regex("name=\"spoofing_enabled\">(true|false)</boolean>").find(content)
            if (enabledMatch != null) {
                val enabled = enabledMatch.groupValues[1] == "true"
                XposedBridge.log("GPS Spoofer: Direct read - spoofing_enabled=$enabled")
                logD("GPS", "GPS Spoofer: Direct read - spoofing_enabled=$enabled")
                
                if (enabled) {
                    // Also extract lat/lon
                    val latMatch = Regex("name=\"latitude\">([^<]+)</string>").find(content)
                    val lonMatch = Regex("name=\"longitude\">([^<]+)</string>").find(content)
                    val lat = latMatch?.groupValues?.get(1)
                    val lon = lonMatch?.groupValues?.get(1)
                    XposedBridge.log("GPS Spoofer: Direct read - Lat=$lat, Lon=$lon")
                    logD("GPS", "GPS Spoofer: Direct read - Lat=$lat, Lon=$lon")
                }
                
                return enabled
            } else {
                XposedBridge.log("GPS Spoofer: Could not find spoofing_enabled in file content")
                logD("GPS", "GPS Spoofer: Could not find spoofing_enabled in file content")
                return false
            }
        } catch (e: Exception) {
            XposedBridge.log("GPS Spoofer: Direct file read failed: ${e.message}, ${e.javaClass.name}")
            logD("GPS", "GPS Spoofer: Direct file read failed: ${e.message}, ${e.javaClass.name}")
            return false
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        if (lpparam == null) {
            return
        }

        // Only hook system framework and Google Play Services for efficiency
        val packageName = lpparam.packageName
        val isSystemFramework = packageName == "android" || packageName == "com.android.server"
        val isGooglePlayServices = packageName == "com.google.android.gms"
        val isTargetApp = packageName == "com.google.android.apps.maps" || 
                         packageName == "com.CyKrome007.GlobalPositioningSystem"
        
        // Always log for critical packages
        if (isSystemFramework || isGooglePlayServices || isTargetApp) {
            XposedBridge.log("GPS Spoofer: Loading package: $packageName")
            logD("GPS", "GPS Spoofer: Loading package: $packageName")
        }
        
        // Test if preferences are accessible (for debugging)
        try {
            prefs.reload()
            val testEnabled = prefs.getBoolean("spoofing_enabled", false)
            val testLat = prefs.getString("latitude", null)
            val testLon = prefs.getString("longitude", null)
            
            // Log to Xposed log for debugging
            if (isSystemFramework || isGooglePlayServices || isTargetApp) {
                XposedBridge.log("GPS Spoofer: Package=$packageName, Enabled=$testEnabled, Lat=$testLat, Lon=$testLon")
                logD("GPS", "GPS Spoofer: Package=$packageName, Enabled=$testEnabled, Lat=$testLat, Lon=$testLon")
            }
        } catch (e: Exception) {
            XposedBridge.log("GPS Spoofer: Failed to read preferences for $packageName: ${e.message}")
            logD("GPS", "GPS Spoofer: Failed to read preferences for $packageName: ${e.message}")
            XposedBridge.log("GPS Spoofer: Exception type: ${e.javaClass.name}, Stack: ${e.stackTraceToString()}")
            logD("GPS", "GPS Spoofer: Exception type: ${e.javaClass.name}, Stack: ${e.stackTraceToString()}")
        }
        
        // Only hook critical packages to avoid performance issues
        if (!isSystemFramework && !isGooglePlayServices && !isTargetApp) {
            return
        }
        
        XposedBridge.log("GPS Spoofer: Setting up hooks for $packageName")
        logD("GPS", "GPS Spoofer: Setting up hooks for $packageName")

        // Hook Location class methods - Core location spoofing
        hookLocationClass(lpparam)
        XposedBridge.log("GPS Spoofer: Location class hooks installed for $packageName")
        logD("GPS", "GPS Spoofer: Location class hooks installed for $packageName")
        
        // Hook LocationManager - System service level
        hookLocationManager(lpparam)
        XposedBridge.log("GPS Spoofer: LocationManager hooks installed for $packageName")
        logD("GPS", "GPS Spoofer: LocationManager hooks installed for $packageName")
        
        // Hook FusedLocationProviderClient - Google Play Services
        hookFusedLocationProvider(lpparam)
        XposedBridge.log("GPS Spoofer: FusedLocationProvider hooks installed for $packageName")
        logD("GPS", "GPS Spoofer: FusedLocationProvider hooks installed for $packageName")
        
        // Hook LocationRequest/Provider for Android 16
        hookLocationProvider(lpparam)
        
        // Hook LocationCallback for FusedLocationProviderClient
        hookLocationCallback(lpparam)
        XposedBridge.log("GPS Spoofer: LocationCallback hooks installed for $packageName")
        logD("GPS", "GPS Spoofer: LocationCallback hooks installed for $packageName")
        
        // Hook advanced FusedLocationProviderClient methods
        hookFusedLocationProviderAdvanced(lpparam)
        XposedBridge.log("GPS Spoofer: All hooks installed for $packageName")
        logD("GPS", "GPS Spoofer: All hooks installed for $packageName")
    }
    
    private fun hookLocationClass(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook getLatitude - Always override when spoofing is enabled
        try {
        XposedHelpers.findAndHookMethod(
            "android.location.Location",
            lpparam.classLoader,
            "getLatitude",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                        if (isSpoofingEnabled()) {
                            val lat = getSpoofedLatitude()
                            if (lat != null) {
                        param.result = lat
                                XposedBridge.log("GPS Spoofer: Intercepted getLatitude() = $lat")
                                logD("GPS", "GPS Spoofer: Intercepted getLatitude() = $lat")
                            }
                    }
                }
            })
        } catch (e: Exception) {
            // Method might not exist
        }

        // Hook getLongitude - Always override when spoofing is enabled
        try {
        XposedHelpers.findAndHookMethod(
            "android.location.Location",
            lpparam.classLoader,
            "getLongitude",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                        if (isSpoofingEnabled()) {
                            val lon = getSpoofedLongitude()
                            if (lon != null) {
                        param.result = lon
                                XposedBridge.log("GPS Spoofer: Intercepted getLongitude() = $lon")
                                logD("GPS", "GPS Spoofer: Intercepted getLongitude() = $lon")
                            }
                        }
                    }
                })
        } catch (e: Exception) {
            // Method might not exist
        }
            
        // Hook setLatitude - Prevent apps from overriding our spoofed location
        try {
            XposedHelpers.findAndHookMethod(
                "android.location.Location",
                lpparam.classLoader,
                "setLatitude",
                Double::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isSpoofingEnabled()) {
                            val lat = getSpoofedLatitude()
                            if (lat != null) {
                                param.args[0] = lat
                            }
                        }
                    }
                })
        } catch (e: Exception) {
            // Method might not exist
        }
        
        // Hook setLongitude - Prevent apps from overriding our spoofed location
        try {
            XposedHelpers.findAndHookMethod(
                "android.location.Location",
                lpparam.classLoader,
                "setLongitude",
                Double::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isSpoofingEnabled()) {
                            val lon = getSpoofedLongitude()
                            if (lon != null) {
                                param.args[0] = lon
                            }
                        }
                    }
                })
        } catch (e: Exception) {
            // Method might not exist
        }

        // Hook getAccuracy - Make it realistic
        XposedHelpers.findAndHookMethod(
            "android.location.Location",
            lpparam.classLoader,
            "getAccuracy",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (isSpoofingEnabled()) {
                        // Return realistic accuracy (5-15 meters)
                    param.result = 10.0f
                    }
                }
            })

        // Hook getSpeed - Make it realistic
        XposedHelpers.findAndHookMethod(
            "android.location.Location",
            lpparam.classLoader,
            "getSpeed",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (isSpoofingEnabled()) {
                        // Return 0 for stationary location
                        param.result = 0.0f
                    }
                }
            })
            
        // Hook getBearing
        XposedHelpers.findAndHookMethod(
            "android.location.Location",
            lpparam.classLoader,
            "getBearing",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (isSpoofingEnabled()) {
                    param.result = 0.0f
                    }
                }
            })
            
        // Hook getAltitude
        XposedHelpers.findAndHookMethod(
            "android.location.Location",
            lpparam.classLoader,
            "getAltitude",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (isSpoofingEnabled()) {
                        prefs.reload()
                        val altitude = prefs.getFloat("altitude", 0.0f)
                        if (altitude != 0.0f) {
                            param.result = altitude.toDouble()
                        }
                    }
                }
            })
    }
    
    private fun hookLocationManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook getLastKnownLocation - return spoofed location even if null
            XposedHelpers.findAndHookMethod(
                "android.location.LocationManager",
                lpparam.classLoader,
                "getLastKnownLocation",
                String::class.java,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (isSpoofingEnabled()) {
                            val lat = getSpoofedLatitude()
                            val lon = getSpoofedLongitude()
                            if (lat != null && lon != null) {
                                val originalLocation = param.result
                                if (originalLocation != null) {
                                    // Modify existing location
                                    XposedHelpers.callMethod(originalLocation, "setLatitude", lat)
                                    XposedHelpers.callMethod(originalLocation, "setLongitude", lon)
                                    XposedHelpers.callMethod(originalLocation, "setAccuracy", 10.0f)
                                    XposedHelpers.callMethod(originalLocation, "setSpeed", 0.0f)
                                    XposedHelpers.callMethod(originalLocation, "setTime", System.currentTimeMillis())
                                    XposedHelpers.callMethod(originalLocation, "setElapsedRealtimeNanos", System.nanoTime())
                                    param.result = originalLocation
                                } else {
                                    // Create new location if null
                                    try {
                                        val locationClass = XposedHelpers.findClass("android.location.Location", lpparam.classLoader)
                                        val location = XposedHelpers.newInstance(locationClass, "spoofed")
                                        XposedHelpers.callMethod(location, "setLatitude", lat)
                                        XposedHelpers.callMethod(location, "setLongitude", lon)
                                        XposedHelpers.callMethod(location, "setAccuracy", 10.0f)
                                        XposedHelpers.callMethod(location, "setSpeed", 0.0f)
                                        XposedHelpers.callMethod(location, "setTime", System.currentTimeMillis())
                                        XposedHelpers.callMethod(location, "setElapsedRealtimeNanos", System.nanoTime())
                                        param.result = location
                                    } catch (e: Exception) {
                                        // Location creation might fail
                                    }
                                }
                            }
                        }
                    }
                })
                
            // Hook requestLocationUpdates - intercept location updates
            XposedHelpers.findAndHookMethod(
                "android.location.LocationManager",
                lpparam.classLoader,
                "requestLocationUpdates",
                String::class.java,
                Long::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                "android.location.LocationListener",
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (isSpoofingEnabled()) {
                            val listener = param.args[3]
                            if (listener != null) {
                                val lat = getSpoofedLatitude()
                                val lon = getSpoofedLongitude()
                                if (lat != null && lon != null) {
                                    // Post spoofed location update
                                    try {
                                        val handlerClass = XposedHelpers.findClass("android.os.Handler", lpparam.classLoader)
                                        val looperClass = XposedHelpers.findClass("android.os.Looper", lpparam.classLoader)
                                        val locationClass = XposedHelpers.findClass("android.location.Location", lpparam.classLoader)
                                        
                                        val mainLooper = XposedHelpers.callStaticMethod(looperClass, "getMainLooper")
                                        val handler = XposedHelpers.newInstance(handlerClass, mainLooper)
                                        
                                        val location = XposedHelpers.newInstance(locationClass, "spoofed")
                                        XposedHelpers.callMethod(location, "setLatitude", lat)
                                        XposedHelpers.callMethod(location, "setLongitude", lon)
                                        XposedHelpers.callMethod(location, "setAccuracy", 10.0f)
                                        XposedHelpers.callMethod(location, "setSpeed", 0.0f)
                                        XposedHelpers.callMethod(location, "setBearing", 0.0f)
                                        XposedHelpers.callMethod(location, "setTime", System.currentTimeMillis())
                                        XposedHelpers.callMethod(location, "setElapsedRealtimeNanos", System.nanoTime())
                                        
                                        val runnable = java.lang.Runnable {
                                            try {
                                                XposedHelpers.callMethod(listener, "onLocationChanged", location)
                                            } catch (e: Exception) {
                                                // Listener might have issues
                                            }
                                        }
                                        XposedHelpers.callMethod(handler, "post", runnable)
                                    } catch (e: Exception) {
                                        // Handler/Location creation might fail
                                    }
                                }
                            }
                        }
                    }
                })
        } catch (e: Exception) {
            // Some methods might not exist in all Android versions
        }
    }
    
    private fun hookFusedLocationProvider(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Check if FusedLocationProviderClient class exists in this classloader
            val fusedLocationProviderClass = XposedHelpers.findClassIfExists(
                "com.google.android.gms.location.FusedLocationProviderClient",
                lpparam.classLoader
            ) ?: return // Class doesn't exist, skip hooking
            
            // Note: getLastLocation() is abstract, so we can't hook it directly
            // Instead, we rely on Location.getLatitude/getLongitude hooks which will intercept
            // the Location objects returned by the Task<Location>
            // The Location class hooks will handle spoofing for all location sources
            
            XposedBridge.log("GPS Spoofer: FusedLocationProviderClient found (abstract methods will be handled by Location class hooks)")
            logD("GPS", "GPS Spoofer: FusedLocationProviderClient found (abstract methods will be handled by Location class hooks)")
        } catch (e: Exception) {
            // FusedLocationProvider might not be available in this package
            if (e is ClassNotFoundException || e.message?.contains("ClassNotFoundException") == true) {
                // This is expected for packages that don't use Google Play Services
                return
            }
            XposedBridge.log("GPS Spoofer: Error checking FusedLocationProvider: ${e.message}")
            logD("GPS", "GPS Spoofer: Error checking FusedLocationProvider: ${e.message}")
        }
    }
    
    private fun hookLocationProvider(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Check if LocationProvider class exists
            val locationProviderClass = XposedHelpers.findClassIfExists(
                "android.location.LocationProvider",
                lpparam.classLoader
            ) ?: return // Class doesn't exist, skip
            
            // Try to hook LocationProvider.getLocation() for Android 16 compatibility
            // This method might not exist in all Android versions, so we catch NoSuchMethodError
            try {
                XposedHelpers.findAndHookMethod(
                    locationProviderClass,
                    "getLocation",
                    object : XC_MethodHook() {
                        @Throws(Throwable::class)
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (isSpoofingEnabled()) {
                                val originalLocation = param.result
                                if (originalLocation != null) {
                                    val lat = getSpoofedLatitude()
                                    val lon = getSpoofedLongitude()
                                    if (lat != null && lon != null) {
                                        XposedHelpers.callMethod(originalLocation, "setLatitude", lat)
                                        XposedHelpers.callMethod(originalLocation, "setLongitude", lon)
                                        param.result = originalLocation
                                    }
                                }
                            }
                        }
                    })
                XposedBridge.log("GPS Spoofer: LocationProvider.getLocation() hook installed")
                logD("GPS", "GPS Spoofer: LocationProvider.getLocation() hook installed")
            } catch (e: NoSuchMethodError) {
                // Method doesn't exist in this Android version - that's okay
                XposedBridge.log("GPS Spoofer: LocationProvider.getLocation() method not found (may not exist in this Android version)")
                logD("GPS", "GPS Spoofer: LocationProvider.getLocation() method not found (may not exist in this Android version)")
            } catch (e: Exception) {
                if (e.message?.contains("NoSuchMethod") == true || e.message?.contains("exact") == true) {
                    // Method signature doesn't match - that's okay
                    XposedBridge.log("GPS Spoofer: LocationProvider.getLocation() signature mismatch (expected for some Android versions)")
                    logD("GPS", "GPS Spoofer: LocationProvider.getLocation() signature mismatch (expected for some Android versions)")
                } else {
                    XposedBridge.log("GPS Spoofer: Error hooking LocationProvider.getLocation(): ${e.message}")
                    logD("GPS", "GPS Spoofer: Error hooking LocationProvider.getLocation(): ${e.message}")
                }
            }
        } catch (e: Exception) {
            // LocationProvider might not exist in all versions
            if (e is ClassNotFoundException || e.message?.contains("ClassNotFoundException") == true) {
                return
            }
            XposedBridge.log("GPS Spoofer: Error hooking LocationProvider: ${e.message}")
            logD("GPS", "GPS Spoofer: Error hooking LocationProvider: ${e.message}")
        }
    }
    
    private fun hookLocationCallback(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Check if LocationCallback class exists in this classloader
            val locationCallbackClass = XposedHelpers.findClassIfExists(
                "com.google.android.gms.location.LocationCallback",
                lpparam.classLoader
            ) ?: return // Class doesn't exist, skip hooking
            
            val locationResultClass = XposedHelpers.findClassIfExists(
                "com.google.android.gms.location.LocationResult",
                lpparam.classLoader
            ) ?: return // LocationResult doesn't exist, skip hooking
            
            // Hook LocationCallback.onLocationResult for FusedLocationProviderClient
            XposedHelpers.findAndHookMethod(
                locationCallbackClass,
                "onLocationResult",
                locationResultClass,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isSpoofingEnabled()) {
                            val locationResult = param.args[0]
                            if (locationResult != null) {
                                try {
                                    // Hook getLastLocation
                                    val originalLocation = XposedHelpers.callMethod(locationResult, "getLastLocation")
                                    if (originalLocation != null) {
                                        val lat = getSpoofedLatitude()
                                        val lon = getSpoofedLongitude()
                                        if (lat != null && lon != null) {
                                            XposedHelpers.callMethod(originalLocation, "setLatitude", lat)
                                            XposedHelpers.callMethod(originalLocation, "setLongitude", lon)
                                            XposedHelpers.callMethod(originalLocation, "setAccuracy", 10.0f)
                                            XposedHelpers.callMethod(originalLocation, "setSpeed", 0.0f)
                                            XposedHelpers.callMethod(originalLocation, "setTime", System.currentTimeMillis())
                                            XposedHelpers.callMethod(originalLocation, "setElapsedRealtimeNanos", System.nanoTime())
                                        }
                                    }
                                    
                                    // Hook getLocations (returns List<Location>) - Important for Google Maps
                                    try {
                                        val locations = XposedHelpers.callMethod(locationResult, "getLocations") as? java.util.List<*>
                                        if (locations != null) {
                                            val lat = getSpoofedLatitude()
                                            val lon = getSpoofedLongitude()
                                            if (lat != null && lon != null) {
                                                for (loc in locations) {
                                                    if (loc != null) {
                                                        XposedHelpers.callMethod(loc, "setLatitude", lat)
                                                        XposedHelpers.callMethod(loc, "setLongitude", lon)
                                                        XposedHelpers.callMethod(loc, "setAccuracy", 10.0f)
                                                        XposedHelpers.callMethod(loc, "setSpeed", 0.0f)
                                                        XposedHelpers.callMethod(loc, "setTime", System.currentTimeMillis())
                                                        XposedHelpers.callMethod(loc, "setElapsedRealtimeNanos", System.nanoTime())
                                                    }
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // getLocations might not exist
                                    }
                                } catch (e: Exception) {
                                    // LocationResult API might be different
                                }
                            }
                        }
                    }
                })
            
            // Hook LocationResult.getLocations() directly - Critical for Google Maps
            try {
                val locationResultClassForHook = XposedHelpers.findClassIfExists(
                    "com.google.android.gms.location.LocationResult",
                    lpparam.classLoader
                ) ?: return // Class doesn't exist, skip
                
                XposedHelpers.findAndHookMethod(
                    locationResultClassForHook,
                    "getLocations",
                    object : XC_MethodHook() {
                        @Throws(Throwable::class)
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (isSpoofingEnabled()) {
                                val locations = param.result as? java.util.List<*>
                                if (locations != null && !locations.isEmpty()) {
                                    val lat = getSpoofedLatitude()
                                    val lon = getSpoofedLongitude()
                                    if (lat != null && lon != null) {
                                        for (loc in locations) {
                                            if (loc != null) {
                                                XposedHelpers.callMethod(loc, "setLatitude", lat)
                                                XposedHelpers.callMethod(loc, "setLongitude", lon)
                                                XposedHelpers.callMethod(loc, "setAccuracy", 10.0f)
                                                XposedHelpers.callMethod(loc, "setSpeed", 0.0f)
                                                XposedHelpers.callMethod(loc, "setTime", System.currentTimeMillis())
                                                XposedHelpers.callMethod(loc, "setElapsedRealtimeNanos", System.nanoTime())
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    })
            } catch (e: Exception) {
                // getLocations method might not exist
            }
            
            // Hook LocationResult.getLastLocation() directly
            try {
                val locationResultClassForLastLocation = XposedHelpers.findClassIfExists(
                    "com.google.android.gms.location.LocationResult",
                    lpparam.classLoader
                ) ?: return // Class doesn't exist, skip
                
                XposedHelpers.findAndHookMethod(
                    locationResultClassForLastLocation,
                    "getLastLocation",
                    object : XC_MethodHook() {
                        @Throws(Throwable::class)
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (isSpoofingEnabled()) {
                                val location = param.result
                                if (location != null) {
                                    val lat = getSpoofedLatitude()
                                    val lon = getSpoofedLongitude()
                                    if (lat != null && lon != null) {
                                        XposedHelpers.callMethod(location, "setLatitude", lat)
                                        XposedHelpers.callMethod(location, "setLongitude", lon)
                                        XposedHelpers.callMethod(location, "setAccuracy", 10.0f)
                                        XposedHelpers.callMethod(location, "setSpeed", 0.0f)
                                        XposedHelpers.callMethod(location, "setTime", System.currentTimeMillis())
                                        XposedHelpers.callMethod(location, "setElapsedRealtimeNanos", System.nanoTime())
                                    }
                                }
                            }
                        }
                    })
            } catch (e: Exception) {
                // getLastLocation method might not exist
            }
        } catch (e: Exception) {
            // LocationCallback might not be available in this package
            if (e is ClassNotFoundException || e.message?.contains("ClassNotFoundException") == true) {
                // This is expected for packages that don't use Google Play Services
                return
            }
            XposedBridge.log("GPS Spoofer: Error hooking LocationCallback: ${e.message}")
            logD("GPS", "GPS Spoofer: Error hooking LocationCallback: ${e.message}")
        }
    }
    
    private fun hookFusedLocationProviderAdvanced(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Check if required classes exist in this classloader
            val fusedLocationProviderClass = XposedHelpers.findClassIfExists(
                "com.google.android.gms.location.FusedLocationProviderClient",
                lpparam.classLoader
            ) ?: return // Class doesn't exist, skip hooking
            
            val locationRequestClass = XposedHelpers.findClassIfExists(
                "com.google.android.gms.location.LocationRequest",
                lpparam.classLoader
            ) ?: return
            
            val locationCallbackClass = XposedHelpers.findClassIfExists(
                "com.google.android.gms.location.LocationCallback",
                lpparam.classLoader
            ) ?: return
            
            val looperClass = XposedHelpers.findClassIfExists(
                "android.os.Looper",
                lpparam.classLoader
            ) ?: return
            
            // Hook requestLocationUpdates with LocationRequest and LocationCallback
            XposedHelpers.findAndHookMethod(
                fusedLocationProviderClass,
                "requestLocationUpdates",
                locationRequestClass,
                locationCallbackClass,
                looperClass,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (isSpoofingEnabled()) {
                            val callback = param.args[1]
                            if (callback != null) {
                                val lat = getSpoofedLatitude()
                                val lon = getSpoofedLongitude()
                                if (lat != null && lon != null) {
                                    // Send spoofed location immediately via Handler
                                    try {
                                        val handlerClass = XposedHelpers.findClass("android.os.Handler", lpparam.classLoader)
                                        val looperClass = XposedHelpers.findClass("android.os.Looper", lpparam.classLoader)
                                        val locationClass = XposedHelpers.findClass("android.location.Location", lpparam.classLoader)
                                        
                                        val mainLooper = XposedHelpers.callStaticMethod(looperClass, "getMainLooper")
                                        val handler = XposedHelpers.newInstance(handlerClass, mainLooper)
                                        
                                        val location = XposedHelpers.newInstance(locationClass, "spoofed")
                                        XposedHelpers.callMethod(location, "setLatitude", lat)
                                        XposedHelpers.callMethod(location, "setLongitude", lon)
                                        XposedHelpers.callMethod(location, "setAccuracy", 10.0f)
                                        XposedHelpers.callMethod(location, "setSpeed", 0.0f)
                                        XposedHelpers.callMethod(location, "setBearing", 0.0f)
                                        XposedHelpers.callMethod(location, "setTime", System.currentTimeMillis())
                                        XposedHelpers.callMethod(location, "setElapsedRealtimeNanos", System.nanoTime())
                                        
                                        // Create LocationResult
                                        val locationResultClass = XposedHelpers.findClass(
                                            "com.google.android.gms.location.LocationResult",
                                            lpparam.classLoader
                                        )
                                        val locationsList = java.util.ArrayList<Any>()
                                        locationsList.add(location)
                                        val locationResult = XposedHelpers.callStaticMethod(
                                            locationResultClass,
                                            "create",
                                            locationsList
                                        )
                                        
                                        // Post with delay using Runnable
                                        val runnable = java.lang.Runnable {
                                            try {
                                                XposedHelpers.callMethod(callback, "onLocationResult", locationResult)
                                            } catch (e: Exception) {
                                                // Callback might fail
                                            }
                                        }
                                        XposedHelpers.callMethod(handler, "postDelayed", runnable, 100L)
                                    } catch (e: Exception) {
                                        // Fallback - location will be intercepted at Location.getLatitude/getLongitude level
                                    }
                                }
                            }
                        }
                    }
                })
        } catch (e: Exception) {
            // Method signature might be different or class not available
            if (e is ClassNotFoundException || e.message?.contains("ClassNotFoundException") == true) {
                // This is expected for packages that don't use Google Play Services
                return
            }
            XposedBridge.log("GPS Spoofer: Error hooking FusedLocationProviderAdvanced: ${e.message}")
            logD("GPS", "GPS Spoofer: Error hooking FusedLocationProviderAdvanced: ${e.message}")
        }
    }
}
