# Frequently Used Commands

## Safe Build Commands

### Primary Build Process
```bash
# ✅ SAFE - Regular debug build (preserves user data)
./gradlew assembleDebug

# ✅ SAFE - Install APK without data loss
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### ⚠️ DANGEROUS Commands
```bash
# ❌ AVOID - Wipes ALL user data (settings, profiles, SkySight credentials)
./gradlew clean

# Only use clean if user explicitly requests it and understands data loss
```

## ADB Commands

### Device Management
```bash
# List connected devices
adb devices

# Install debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Uninstall app
adb uninstall com.example.xcpro
```

### Debugging
```bash
# View live logs
adb logcat

# Filter logs for your app
adb logcat | grep "com.example.xcpro"

# Clear log buffer
adb logcat -c

# View crash logs
adb logcat -d | grep "AndroidRuntime"
```

### File Operations
```bash
# Pull files from device
adb pull /sdcard/path/to/file ./local/path

# Push files to device
adb push ./local/file /sdcard/path/to/destination

# Access device shell
adb shell
```

## Gradle Commands

### Build Variants
```bash
# Debug build (default)
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Build all variants
./gradlew assemble
```

### Testing
```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Generate test reports
./gradlew jacocoTestReport
```

### Code Quality
```bash
# Run lint checks
./gradlew lint

# Run lint and generate report
./gradlew lintDebug

# Fix lint issues automatically
./gradlew lintFix
```

## Project Analysis

### APK Information
```bash
# Get APK info (requires aapt)
aapt dump badging app/build/outputs/apk/debug/app-debug.apk

# List APK contents
aapt list app/build/outputs/apk/debug/app-debug.apk
```

### Dependency Management
```bash
# View dependency tree
./gradlew dependencies

# Check for dependency updates
./gradlew dependencyUpdates

# View project properties
./gradlew properties
```

## Git Commands (when repo is initialized)

### Basic Operations
```bash
# Initialize repository
git init

# Add remote origin
git remote add origin <repository-url>

# Check status
git status

# Stage changes
git add .

# Commit changes
git commit -m "Commit message"

# Push to remote
git push origin main
```

## Testing Commands

### Critical Racing Task Tests
```bash
# Run app and test finish cylinder radius changes
# 1. Change finish cylinder radius (5km -> 0.5km)
# 2. Verify total task distance changes
# 3. Test turnpoint cylinder radii modifications
# 4. Switch between START_LINE and START_CYLINDER
# 5. Verify course display matches calculations
```

## Android Studio Integration

### Preferred Testing Method
- Run from Android Studio (preserves all user data)
- Use built-in debugger for crash analysis
- Utilize Android Studio's APK Analyzer

### Alternative Command Line Testing
```bash
# Build and install in one command
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Troubleshooting

### Common Issues
```bash
# Clear Gradle cache
./gradlew clean build --refresh-dependencies

# Reset ADB
adb kill-server && adb start-server

# Check device storage
adb shell df

# View system properties
adb shell getprop
```

### Performance Profiling
```bash
# Memory usage
adb shell dumpsys meminfo com.example.xcpro

# CPU usage
adb shell top | grep com.example.xcpro

# Network usage
adb shell dumpsys netstats
```