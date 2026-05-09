# Dual Config + Smart Parser - v1.6.1

## Changes Made

### ✅ Idea 1: Smart Input Parser

**Problem:** User needs to manually clean verbose output before pasting into scanner.

**Solution:** Parser now extracts domains/IPs from "whatever bullshit" input format.

**Supported formats:**
```
✅ Target Domain: example.com
✅ Domain: example.com
✅ Resolved IPs: 1.2.3.4
✅ example.com | A_RECORD | 1.2.3.4 (CSV)
✅ example.com (plain)
✅ Random text with example.com hidden inside
✅ --- separator lines (ignored)
✅ # comments (ignored)
✅ Failed to resolve... (ignored)
✅ Curl Status: ... (ignored)
```

**Example input:**
```
Target Domain: tvd-packages.tradingview.com
Resolved IPs: 84.17.46.49
Curl Status: No Response / Blocked
----------------------------------------
Target Domain: s3-symbol-logo.tradingview.com
Resolved IPs: 84.17.46.49
Curl Status: No Response / Blocked
----------------------------------------
Target Domain: load.sumome.com
Failed to resolve DNS or connection blocked.
```

**Parser extracts:**
```
tvd-packages.tradingview.com
s3-symbol-logo.tradingview.com
(load.sumome.com skipped - "Failed to resolve" line)
```

**Code changes:**
- `extractDomainCandidates()` - Added regex patterns for verbose formats
- Backward compatible with CSV and plain formats

---

### ✅ Idea 2: Dual Config Generation (Domain Fronting Only)

**Problem:** Users need both IP-based and domain-based configs for different network conditions.

**Solution:** Scanner generates BOTH configs but shows only one result row (no UI changes).

**How it works:**

**Scanner tests:**
- DNS resolution: `tvd-packages.tradingview.com` → `84.17.46.49`
- TLS + HTTP/2 probe to `84.17.46.49:443` with SNI `tvd-packages.tradingview.com`
- Xray validation via resolved IP

**Scanner generates 2 configs internally:**

**Config 1 - IP-based (recommended):**
```
vless://...@84.17.46.49:443?sni=tvd-packages.tradingview.com&host=wsgtgp.b-cdn.net...#IP-45ms
```
- `address` = IP (84.17.46.49)
- Works without DNS setup on phone ✅

**Config 2 - Domain-based (requires custom DNS):**
```
vless://...@tvd-packages.tradingview.com:443?sni=tvd-packages.tradingview.com&host=wsgtgp.b-cdn.net...#DOMAIN-45ms
```
- `address` = domain (tvd-packages.tradingview.com)
- Requires DoH or custom DNS on phone ⚙️

---

## UI Behavior (Zero Changes)

**Same 4 buttons:**
- V2Ray→NetMod
- Copy IP
- Speed Test
- Copy Config

**Same layout, same appearance.**

**Copy behavior updated:**

### When user clicks "V2Ray→NetMod" or "Copy Config":

**CloudFlare / CloudFront (no change):**
```
Copied 1 config
```

**Domain Fronting:**
```
Copied 2 configs

Clipboard contains:
# IP-based (recommended, no DNS setup needed)
vless://...@84.17.46.49:443?sni=tvd-packages.tradingview.com...#IP-45ms

# Domain-based (requires custom DNS on phone)
vless://...@tvd-packages.tradingview.com:443?sni=tvd-packages.tradingview.com...#DOMAIN-45ms
```

### When user clicks global "COPY ALL" button:

**Toast shows:**
```
Copied 20 config(s) from 10 result(s)
```
(10 Domain Fronting results = 20 configs, 10 CloudFlare results = 10 configs)

---

## Code Changes Summary

### File: MainActivity.kt

**1. DisplayResult data class** (line 679)
```kotlin
data class DisplayResult(
    val ip: String,
    val builtConfig: String,
    val builtConfigDomain: String = "",  // NEW: Domain-based config
    // ... rest unchanged
)
```

**2. extractDomainCandidates()** (line 2366)
- Added regex patterns for verbose input
- `Target Domain: <domain>` → extract
- `Resolved IPs?: <ip>` → extract
- Fallback to CSV and plain formats
- Backward compatible

**3. DisplayResult creation** (line 2090)
```kotlin
val ipBasedConfig = buildUri(validationBase, probe.ip, status, probe.resolvedIp)
val domainBasedConfig = if (core == DOMAIN_FRONTING && probe.resolvedIp.isNotBlank()) {
    buildUri(validationBase, probe.ip, status, "")  // resolvedIp="" = use domain as address
} else ""
```

**4. Helper function** (line 2802)
```kotlin
private fun formatDualConfigs(result: DisplayResult): String {
    return if (result.builtConfigDomain.isNotBlank()) {
        "# IP-based (recommended, no DNS setup needed)\n" +
        result.builtConfig + "\n\n" +
        "# Domain-based (requires custom DNS on phone)\n" +
        result.builtConfigDomain
    } else result.builtConfig
}
```

**5. Updated buttons:**
- `openInNetMod()` - copies both configs
- "Copy Config" button - copies both configs
- Global "COPY" buttons (3 tabs) - copies both configs for all results

---

## Testing Checklist

### Test 1: Smart Parser
1. Copy the example verbose input above
2. Paste into Domain Fronting tab
3. **Expected:** Extracts 2 domains, ignores garbage
4. **Log shows:** "Filtered X invalid entries. Scanning Y valid domains."

### Test 2: Dual Configs
1. Scan 1 domain: `tvd-packages.tradingview.com`
2. Click "Copy Config" button
3. **Expected:** Toast shows "2 configs copied"
4. Paste clipboard → See both IP and domain configs with comments

### Test 3: Global COPY
1. Scan 5 domains in Domain Fronting
2. Get 3 successful results
3. Click global "COPY" button
4. **Expected:** Toast shows "6 config(s) from 3 result(s)"
5. Paste clipboard → See 6 configs total (3 IP + 3 domain)

### Test 4: CloudFlare (no change)
1. Switch to CloudFlare mode
2. Scan IPs
3. Click "Copy Config"
4. **Expected:** Toast shows "1 config copied" (no dual)

### Test 5: NetMod Integration
1. Scan Domain Fronting
2. Click "V2Ray→NetMod"
3. **Expected:** Both configs copied, NetMod opens
4. **Toast:** "2 configs copied • Opening NetMod"

---

## Benefits

**Idea 1 - Smart Parser:**
- ✅ Copy-paste from any DNS tool, curl output, ping results
- ✅ No manual cleanup needed
- ✅ Filters garbage automatically
- ✅ Backward compatible with CSV and plain formats

**Idea 2 - Dual Configs:**
- ✅ IP-based: Works without DNS setup (safe default)
- ✅ Domain-based: Works if user has DoH app (flexibility)
- ✅ User gets both options without extra scan time
- ✅ Clean UI - no duplicate result rows
- ✅ Only applies to Domain Fronting mode

---

## Version

- **Added in:** v1.6.1
- **Files changed:** `MainActivity.kt` only
- **UI changes:** Zero (same buttons, same layout)
- **Copy behavior:** Enhanced to include both configs

---

**Both ideas implemented!** ✅
