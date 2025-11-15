# How to View Logs

## Method 1: Using ADB Logcat (Recommended)

Connect your phone via USB and run:

### View all GPS logs:
```bash
adb logcat -s GPS:* LocationSearch:*
```

### View only debug logs:
```bash
adb logcat -s GPS:D LocationSearch:D
```

### View logs in real-time with timestamps:
```bash
adb logcat -v time -s GPS:* LocationSearch:*
```

### Clear log buffer and start fresh:
```bash
adb logcat -c && adb logcat -s GPS:* LocationSearch:*
```

## Method 2: LSPosed Manager Logs

1. Open **LSPosed Manager** app on your phone
2. Go to **Logs** section
3. Filter by "GPS Spoofer" or "GPS"
4. You'll see logs from the Xposed module (LocationSpoofer.kt)

## Method 3: Android Studio Logcat

If you're using Android Studio:
1. Connect your phone via USB
2. Open **Logcat** tab at the bottom
3. Filter by tag: `GPS` or `LocationSearch`
4. Select your device from the device dropdown

## Method 4: View Both App and Xposed Logs

To see logs from both the app and Xposed module:

```bash
# View app logs (Android Logcat)
adb logcat -s GPS:* LocationSearch:*

# In another terminal, view LSPosed logs
adb logcat -s LSPosed-Bridge:*
```

## Log Tags Used:

- **GPS** - Main app logs (MainActivity.kt, App.kt)
- **LocationSearch** - Search functionality logs
- **LSPosed-Bridge** - Xposed module logs (LocationSpoofer.kt)

## Quick Test:

1. Start the app
2. Try to start spoofing
3. Run: `adb logcat -s GPS:* | grep -i "spoofing"`
4. You should see logs like:
   ```
   GPS: Initializing isSpoofingActive: enabled=false, lat=null, lon=null
   GPS: Spoofing STARTED - Lat=37.7749, Lon=-122.4194, Committed=true
   ```

