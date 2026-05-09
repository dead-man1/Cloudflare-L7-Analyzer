# Build Instructions - CF L7 Scanner v1.6.0

## Pre-Build Checklist

✅ All code changes complete:
- `MainActivity.kt` — Domain Fronting core + DNS integration
- `DnsResolver.kt` — DNS fallback chain (new file)
- `XrayBridge.kt` — xhttp support added
- `build.gradle.kts` — Version bumped to 1.6.0

✅ Version numbers updated:
- `build.gradle.kts`: `versionCode = 3`, `versionName = "1.6.0"`
- `MainActivity.kt`: `APP_VERSION = "v1.6.0"`

---

## Build Steps

### 1. Open Android Studio
```
Open project: C:\Users\ehsan\AndroidStudioProjects\HostExtractor
```

### 2. Sync Gradle
- Wait for "Gradle sync" to complete (bottom status bar)
- If errors appear, check:
  - JDK version (Java 11+ required)
  - Kotlin plugin version
  - Gradle cache (File → Invalidate Caches if needed)

### 3. Build APK
**Option A: Debug build (faster, for testing)**
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

**Option B: Release build (optimized, for distribution)**
```
Build → Generate Signed Bundle / APK
→ APK
→ Select keystore (or create new)
→ Build release APK
```
Output: `app/build/outputs/apk/release/app-release.apk`

### 4. Install on Device
**Via Android Studio:**
- Connect device via USB (USB debugging enabled)
- Click green "Run" button (or Shift+F10)

**Via ADB:**
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Testing Checklist

### Basic Functionality
- [ ] App launches without crashes
- [ ] Home screen shows 3 cores (Cloudflare, CloudFront, Domain Fronting)
- [ ] Domain Fronting core has green theme
- [ ] Bottom nav shows 3 items

### Domain Fronting Core
- [ ] INPUT tab: Custom DNS field visible (only for Domain Fronting)
- [ ] DOMAIN FRT tab: Load domains file works
- [ ] Paste domain list (5-10 domains)
- [ ] Start scan → progress updates correctly
- [ ] Pause button → scan pauses
- [ ] Resume button → scan continues
- [ ] Stop button → scan stops cleanly

### DNS Resolution (via Logcat)
```bash
adb logcat | grep SCANDBG
```
Expected logs:
```
SCANDBG: dns resolve domain=a16z.com resolved=104.16.xxx.xxx customDns=8.8.8.8
SCANDBG: dns resolve domain=airbnb.com resolved=13.224.xxx.xxx customDns=none
```

### Output Format
- [ ] Result IP shows domain (e.g., "a16z.com"), not resolved IP
- [ ] Copy config → VLESS link has `@domain:443`, not `@ip:443`
- [ ] SNI parameter matches domain
- [ ] Host parameter matches template (e.g., `host=newfrz2.netlify.app`)

### xhttp Support
- [ ] INPUT tab: xhttp toggle chip visible
- [ ] Paste xhttp config → fields auto-populate
- [ ] Save → load → fields persist
- [ ] Scan with xhttp transport → probes succeed (not WS_REJECTED)

### Existing Cores (Regression Check)
- [ ] Cloudflare core still works (orange theme)
- [ ] CloudFront core still works (purple theme)
- [ ] IP SCAN tab works for both
- [ ] CONFIG SCAN tab works for both

---

## Common Build Issues

### Issue: "Unresolved reference: Language"
**Fix:** Import missing in MainActivity.kt
```kotlin
import androidx.compose.material.icons.filled.Language
```
Already added — if error persists, Invalidate Caches.

### Issue: "DnsResolver not found"
**Fix:** File not in correct package
- Ensure `DnsResolver.kt` is in `app/src/main/java/com/example/hostextractor/`
- Package declaration: `package com.example.hostextractor`

### Issue: Gradle sync fails
**Fix:**
```bash
# Clear Gradle cache
./gradlew clean
# Or in Android Studio: File → Invalidate Caches → Invalidate and Restart
```

### Issue: APK install fails on device
**Fix:**
- Enable "Install from unknown sources" in device settings
- Uninstall old version first if signature mismatch
- Check USB debugging enabled

---

## Logcat Debug Commands

### View DNS resolution logs:
```bash
adb logcat | grep "dns resolve"
```

### View scan progress logs:
```bash
adb logcat | grep "SCANDBG"
```

### View Xray validation logs:
```bash
adb logcat | grep "XRAYDBG"
```

### Clear logcat before test:
```bash
adb logcat -c
```

---

## Performance Benchmarks (Expected)

**Test setup:** 10 domains, xhttp+tls config, Custom DNS = 8.8.8.8

| Stage | Time | Notes |
|-------|------|-------|
| DNS resolution | 2-5s per domain | UDP = fastest, DoH = slowest |
| Fast scan | 10-20s total | 40 workers, single port |
| Top 100 filter | <1s | Should get 5-8 passing domains |
| Final validation | 30-60s | 8 workers, xray validation |
| **Total** | **2-4 minutes** | Depends on network/DNS speed |

**Memory usage:**
- Idle: ~80 MB
- Scanning: ~200-300 MB
- Peak (final validation): ~400 MB

---

## Next Steps After Build

1. **Local testing:**
   - Install APK on your device
   - Run through testing checklist above
   - Check Logcat for errors
   - Verify at least 1-2 domains pass final validation

2. **Real-world test:**
   - Import working config to NetMod/v2rayNG
   - Test actual tunnel connection
   - Measure latency vs. IP-based config

3. **If all tests pass:**
   - Commit changes to Git
   - Push to GitHub
   - Create release tag `v1.6.0`

4. **If issues found:**
   - Note specific error/crash in Logcat
   - Share with me for debugging
   - DO NOT push to GitHub yet

---

## Git Commit Message Template

```
feat: Add Domain Fronting scanner + DNS fallback v1.6.0

Major features:
- New Domain Fronting core (Mode 1) for CDN domain scanning
- 3-tier DNS fallback: UDP → System → DoH (bypasses poisoning)
- xhttp/splithttp transport support fixed
- Custom DNS field in INPUT tab (Domain Fronting only)

Technical changes:
- Added DnsResolver.kt (UDP/DoH with poison filter)
- Modified MainActivity.kt (+~200 lines)
- Modified XrayBridge.kt (xhttp case)
- Version bump: 1.5.6 → 1.6.0

Testing:
- Domain fronting scan: ✅ working
- DNS fallback: ✅ verified in Logcat
- xhttp probe: ✅ accepts 200/403/404
- Existing cores: ✅ no regression

Closes #XX (if GitHub issue exists)
```

---

**Ready to build!** 🚀

Open Android Studio now and follow the steps above.
