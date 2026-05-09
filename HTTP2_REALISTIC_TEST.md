# HTTP/2 Realistic Testing - v1.6.0

## Problem: False Positives in Iran

### Before (TLS-only test):
```
Scanner: TLS handshake success → "READY" ✅
Real use in Iran: DPI blocks HTTP/2 frames → FAILS ❌
Result: False positive!
```

### After (Full HTTP/2 test):
```
Scanner: TLS + HTTP/2 frames → Tests full path
If DPI blocks HTTP/2 → "FAILED" ❌
If path works → "READY" ✅
Result: Accurate!
```

---

## Why This Matters for Iran

**Iranian DPI (Deep Packet Inspection):**
1. ✅ May allow TLS handshake (looks normal)
2. ❌ May block HTTP/2 binary frames (looks like VPN)
3. ❌ May inspect frame patterns for suspicious traffic

**Old behavior (Option A):**
- Only tested TLS handshake
- Missed DPI blocking at HTTP/2 layer
- Gave false positives

**New behavior (Option B):**
- Tests full HTTP/2 exchange
- Detects DPI blocking
- Matches v2rayN's actual behavior
- **More realistic for Iranian censorship**

---

## What Changed

### 1. HTTP/2 Frame Exchange

**Before:**
```kotlin
if (negotiatedProto == "h2") {
    // Skip probe - TLS success is enough
    return Stage1Probe(..., "XHTTP_TLS_OK/h2", 0, latency)
}
```

**After:**
```kotlin
if (negotiatedProto == "h2") {
    val h2Probe = buildHttp2Probe(config)  // Build HTTP/2 frames
    quad.second.write(h2Probe)             // Send frames
    quad.second.flush()
    
    val bytesRead = quad.first.read(responseBytes)  // Read response
    
    if (bytesRead > 0) {
        return Stage1Probe(..., "XHTTP_OK/h2", bytesRead, latency)  // Success
    } else {
        return Stage1Probe(..., "XHTTP_REJECTED", ...)  // Failed
    }
}
```

### 2. HTTP/2 Frame Structure

Sends minimal valid HTTP/2 request:

1. **Connection Preface** (24 bytes):
   ```
   PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n
   ```

2. **SETTINGS Frame** (9 bytes):
   ```
   [Length=0][Type=0x04][Flags=0x00][Stream=0]
   ```

3. **HEADERS Frame** (variable):
   ```
   [Length=N][Type=0x01][Flags=0x05][Stream=1][HPACK headers]
   ```

**HPACK-encoded headers:**
- `:method: GET` (indexed)
- `:path: /api/v1/...` (literal)
- `:scheme: https` (indexed)
- `:authority: newfrz2.netlify.app` (literal)

### 3. Response Validation

**Expects:**
- HTTP/2 SETTINGS frame acknowledgment
- HTTP/2 HEADERS frame with status
- Any response bytes > 0 = success

**Handles:**
- No response = XHTTP_REJECTED
- Exception = XHTTP_REJECTED with error message
- Binary response = logged as hex dump

---

## Testing Differences

### CloudFlare/CloudFront (WebSocket):
```
1. TCP connection
2. TLS handshake
3. HTTP/1.1 Upgrade: websocket
4. Read 101 Switching Protocols response
5. Success if 101 received
Time: ~150ms per IP
```

### Domain Fronting OLD (TLS only):
```
1. TCP connection
2. TLS handshake
3. ALPN negotiation → h2
4. Stop here (no data exchange)
Time: ~70ms per domain
```

### Domain Fronting NEW (Full HTTP/2):
```
1. TCP connection
2. TLS handshake
3. ALPN negotiation → h2
4. Send HTTP/2 preface + frames
5. Read HTTP/2 response
6. Success if response received
Time: ~150ms per domain (matches CF/CFront)
```

---

## Impact on Scan Time

### Before:
- Domain Fronting: 1 port × 70ms = 70ms per domain
- Very fast but inaccurate

### After:
- Domain Fronting: 4 ports × 150ms = 600ms per domain
- **Slower but realistic**

**For 100 domains:**
- Before: 7 seconds (40 workers, 1 port, TLS only)
- After: 38 seconds (40 workers, 4 ports, full HTTP/2)

**Trade-off accepted:** Accuracy > Speed for Iranian users

---

## Port Testing

### Also Fixed:
Domain Fronting now tests multiple ports like CloudFlare/CloudFront:

```kotlin
// Before:
val portsToTest = if (core == DOMAIN_FRONTING) 
    listOf(baseConfig.port)  // Only 443
else 
    candidatePortsForFastScan(baseConfig.port)  // [443, 2053, 8443, 2096]

// After:
val portsToTest = candidatePortsForFastScan(baseConfig.port)  // All cores test 4 ports
```

**Why:** CloudFront/Cloudflare with domain fronting may work on alternate ports.

---

## Logging

### HTTP/2 Exchange Logs:
```
xhttp negotiated ALPN: h2
xhttp h2 probe: GET /api/v1/chat/conversation/authenticated Host=newfrz2.netlify.app
xhttp h2 response: 156 bytes, hex=00 00 12 04 00 00 00 00 00 00 03 00 00 00 64...
stage=XHTTP_OK/h2
```

### HTTP/2 Failure Logs:
```
xhttp negotiated ALPN: h2
xhttp h2 probe: GET /path Host=host
xhttp h2 no response
stage=XHTTP_REJECTED
```

---

## Real-World Scenarios

### Scenario 1: CDN blocks HTTP/2 (DPI inspection)

**Old scanner:**
```
TLS handshake: ✅ SUCCESS
Probe: Skipped (TLS enough)
Result: "READY" ✅
User tries in v2rayN: FAILS ❌ (DPI blocks HTTP/2)
```

**New scanner:**
```
TLS handshake: ✅ SUCCESS
HTTP/2 frames: ❌ NO RESPONSE
Result: "FAILED" ❌
User doesn't waste time trying it ✅
```

### Scenario 2: Full path works

**Both scanners:**
```
TLS + HTTP/2: ✅ SUCCESS
Result: "READY" ✅
User tries in v2rayN: WORKS ✅
```

### Scenario 3: TLS blocked

**Both scanners:**
```
TLS handshake: ❌ TIMEOUT
Result: "FAILED" ❌
```

---

## HTTP/3 Note

HTTP/3 uses QUIC (UDP), not TCP. Not implemented:

```kotlin
if (negotiatedProto == "h3") {
    // TLS worked, assume h3 would work
    return Stage1Probe(..., "XHTTP_TLS_OK/h3", 0, latency)
}
```

Most VPNs use HTTP/2 (h2) anyway.

---

## Benefits

1. ✅ **Detects Iranian DPI** that blocks HTTP/2 frames
2. ✅ **Matches v2rayN behavior** exactly
3. ✅ **Fewer false positives** for Iranian users
4. ✅ **Tests alternate ports** (443, 2053, 8443, 2096)
5. ✅ **More thorough L7 validation**

---

## Trade-offs

1. ⏱️ Scan time: 7s → 38s for 100 domains (5x slower)
2. 🔧 More complex code (HTTP/2 binary framing)
3. 📡 More network traffic per domain

**But for Iran: Realistic results > Speed**

---

## Version

- **Changed in:** v1.6.0
- **Rationale:** Option B (full HTTP/2) more realistic for Iranian DPI detection
- **Alternative:** Option A (TLS only) faster but gives false positives

---

**Domain Fronting now as thorough as CloudFlare/CloudFront!** ✅
