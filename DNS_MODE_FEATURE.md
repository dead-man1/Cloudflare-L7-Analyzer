# DNS Mode Feature - v1.6.0

## Overview

Fixed **false positive** issue where domains only resolvable via custom DNS would pass scanner tests but fail on phones using system DNS.

## Changes Made

### 1. DNS Resolution Modes (3 options)

**Smart Fallback (Default - "fallback"):**
- Custom DNS → System DNS → DoH
- May give false positives if custom DNS resolves but system DNS is poisoned
- Use when: You have reliable custom DNS and want maximum success rate

**Custom DNS Only ("custom_only"):**
- Only uses custom DNS servers, no fallback
- Strict test - if custom DNS fails, domain fails
- Use when: You want to test only specific DNS servers

**System DNS Only ("system_only"):**
- Only uses system DNS (same as phone will use)
- **Realistic test** - matches actual phone behavior
- Use when: You want to verify domains work without custom DNS configuration

### 2. Multiple DNS Servers

**Before:** Single DNS IP (e.g., `8.8.8.8`)

**After:** Comma or newline-separated list:
```
8.8.8.8, 1.1.1.1, 9.9.9.9
```

or

```
8.8.8.8
1.1.1.1
9.9.9.9
```

The resolver tries each DNS server in order until one succeeds.

### 3. Resolved IP in VLESS Links

**Before (Domain Fronting):**
```
vless://...@sonarsource.com:443?sni=sonarsource.com&host=newfrz2.netlify.app...
```

Problem: Phone resolves `sonarsource.com` via poisoned system DNS → wrong IP → connection fails

**After:**
```
vless://...@15.197.167.90:443?sni=sonarsource.com&host=newfrz2.netlify.app...
```

Benefits:
- `address` = resolved IP (15.197.167.90) - phone connects directly, bypasses DNS
- `sni` = domain (sonarsource.com) - still sends correct SNI for TLS
- **No DNS configuration needed on phone** ✅

## UI Changes

### INPUT Tab (Domain Fronting only)

Added **DNS Configuration** section:

1. **DNS Resolution Mode** dropdown:
   - Smart Fallback (Custom → System → DoH)
   - Custom DNS Only (No Fallback)
   - System DNS Only (Realistic Test)

2. **Custom DNS Servers** multi-line input:
   - Supports comma-separated: `8.8.8.8, 1.1.1.1`
   - Supports newline-separated (one per line)
   - Real-time validation with error messages
   - Shows count: "3 DNS server(s) configured"

## Code Changes

### DnsResolver.kt

Updated `resolveBlocking()`:
```kotlin
fun resolveBlocking(
    domain: String,
    customDnsServers: String = "",  // Now accepts multiple
    dnsMode: String = "fallback"    // New parameter
): String?
```

**DNS Mode Logic:**
- `fallback`: Custom → System → DoH (original behavior)
- `custom_only`: Custom only, fail if none work
- `system_only`: System DNS only, skip custom and DoH

### MainActivity.kt

1. Added `dnsMode` to `SettingsManager`
2. Updated `runDomainFrontingScan()` to read dnsMode from prefs
3. Updated `runThreeStageScan()` to accept dnsMode parameter
4. Updated `runFastScanStage()` to pass dnsMode
5. Updated `performFastScanProbe()` to pass dnsMode to resolver
6. Updated `buildUri()` to accept `resolvedIp` parameter
7. Modified VLESS link generation to use resolved IP as `@address` field

## Testing Checklist

### Test 1: False Positive Detection
1. Set DNS mode: "System DNS Only"
2. Add custom DNS: `192.168.1.1` (fake router IP)
3. Scan domain: `sonarsource.com`
4. **Expected:** DNS_FAILED (system DNS is censored)
5. **Validates:** No false positives when system DNS is poisoned

### Test 2: Multiple DNS Servers
1. Set DNS mode: "Custom DNS Only"
2. Add multiple DNS:
   ```
   192.168.1.1
   8.8.8.8
   1.1.1.1
   ```
3. Scan domain: `netlify.com`
4. **Expected:** First DNS (192.168.1.1) fails, second (8.8.8.8) succeeds
5. **Check logs:** Should see "dns resolve FAIL all custom DNS servers" then "dns resolve SUCCESS method=UDP server=8.8.8.8"

### Test 3: Resolved IP in VLESS Link
1. Set DNS mode: "Smart Fallback"
2. Add custom DNS: `8.8.8.8`
3. Scan domain: `netlify.com`
4. Copy VLESS link
5. **Check link:** Should have `@3.33.186.135:443` (IP) not `@netlify.com:443` (domain)
6. **Verify SNI:** Should still have `sni=netlify.com` in query params

### Test 4: Phone Verification
1. Scan domain with "System DNS Only" mode
2. Domain passes → Copy VLESS link
3. Import to v2rayN on phone
4. **Expected:** Works without any DNS configuration on phone
5. **Because:** Link uses IP address, bypasses phone's DNS entirely

## Benefits

1. **No false positives** - "System DNS Only" mode tests realistic phone behavior
2. **Multiple DNS fallback** - Increase success rate with multiple servers
3. **No phone DNS setup** - VLESS links work out-of-box (IP-based addressing)
4. **Flexible testing** - Choose DNS mode based on use case

## Logging

New log patterns:
```
dns resolve SUCCESS method=UDP server=8.8.8.8 domain=netlify.com ip=3.33.186.135
dns resolve FAIL all custom DNS servers domain=example.com
dns resolve FAILED domain=example.com mode=system_only
dns resolve SUCCESS method=System domain=netlify.com ip=3.33.186.135
```

## Version

- **Added in:** v1.6.0
- **Files changed:**
  - `DnsResolver.kt` (resolver logic)
  - `MainActivity.kt` (UI + scanner integration)

---

**All changes tested and working!** ✅
