# Android Studio Sync Instructions

## To ensure Android Studio uses the latest code:

1. **In Android Studio:**
   - Click "File" → "Invalidate Caches..."
   - Check all options:
     - ✅ Clear file system cache and Local History
     - ✅ Clear VCS Log caches and indexes
   - Click "Invalidate and Restart"

2. **After restart:**
   - Click "Build" → "Clean Project"
   - Click "Build" → "Rebuild Project"
   - Or use: Build → Make Project (Ctrl+F9)

3. **To access SkySight settings:**
   - Open the app
   - Use the hamburger menu (☰) in top-left
   - Navigate to "General Settings"
   - Find the "SkySight Weather" section
   - Enter your SkySight credentials

## What's been added:
- SkySight login screen in Settings
- Weather overlay controls (FAB button)
- Enhanced debugging for HTTP 500 errors
- Automatic parameter testing for API calls
- Tile coordinate validation

## Testing SkySight:
1. Login with your SkySight credentials in Settings
2. Return to map screen
3. Click the cloud FAB button (bottom-right)
4. Toggle satellite/rain overlays

## Debug logs:
```bash
adb logcat -s SkysightLayers,SkysightDebug,SkysightFAB,SkysightClient
```