# CF L7 Scanner v1.6.0 Release Notes

**Release Date:** 2025-01-XX  
**Previous Version:** 1.5.6

---

## 🎯 Major Features

### ✨ Domain Fronting Scanner (Mode 1)
New 3rd scanner core for CDN domain fronting attacks (Netlify, Vercel, etc.)

**How it works:**
- Scan CDN domains (e.g., `a16z.com`, `airbnb.com`) instead of IPs
- Client connects with domain as SNI → ISP sees innocent CDN domain
- CDN routes via `Host` header to your VPN server subdomain
- **Result:** Bypasses SNI-based censorship in Iran

**UI:**
- Green-themed 3rd core: "Domain Fronting Scanner"
- Bottom nav: "Domain FRT" tab
- Domain list input (one per line, supports file import)
- Single-port scanning (no multi-port fallback)

**Config output:**
```
address=a16z.com (domain, not IP)
sni=a16z.com (domain for TLS handshake)
host=newfrz2.netlify.app (your CDN app subdomain, routes to VPN)
```

---

### 🌐 DNS Fallback Chain (Anti-Censorship)
3-tier DNS resolution for domain fronting to bypass Iranian DNS poisoning

**Fallback order:**
1. **Custom UDP DNS** (user-specified, e.g., `8.8.8.8:53`)
2. **System DNS** with poison filter (blocks known Iranian fake IPs)
3. **DoH (DNS over HTTPS)** — Cloudflare 1.1.1.1 / Quad9

**Poison IP blocklist:**
- `10.10.34.34-36`, `78.39.198.34-35`, `85.15.9.138-139`
- `10.10.0.1-2`, `127.0.0.1`, `0.0.0.0`

**UI:**
- New field in INPUT tab (Domain Fronting core only)
- "Custom DNS server (optional, e.g. 8.8.8.8)"
- Leave empty → auto-uses system DNS + DoH fallback

**Technical:**
- DoH providers use direct IPs → no DNS bootstrap needed
- UDP DNS: raw packet parsing (A record extraction)
- All failures logged via `ScannerLog`

---

### 🔧 xhttp Transport Support
Fixed xhttp/splithttp probe support (was broken, only ws+grpc worked)

**Changes:**
- `performStage1Probe()` now detects xhttp transport
- Sends HTTP GET (not WebSocket upgrade)
- Accepts 1xx-4xx status codes (200/403/404 = L7 reached)
- Rejects 0/5xx (server error = failed probe)
- `XrayBridge.kt` now builds `xhttpSettings` block correctly

**UI:**
- Added "xhttp" toggle chip in INPUT tab

---

## 📊 Performance

**Worker counts:**
- Domain Fronting fast scan: 40 workers (vs 88 for Cloudflare, 56 for CloudFront)
- Domain Fronting final validation: 8 workers (same as CloudFront)
- Single port per domain (no 2053/8443/2096 fallback)

**Timeouts:**
- DNS resolution: 3s UDP, 5s DoH per provider
- Fast scan: 6s timeout, 450ms keepalive
- Final validation: 12s timeout, 5s keepalive (same as CloudFront)

---

## 🐛 Bug Fixes

- ✅ xhttp configs now parse and probe correctly
- ✅ Mode 1 output uses domain as `address` (was using IP before)
- ✅ DNS poisoning bypassed for domain scanning

---

## 🏗️ Technical Details

### New Files
- `DnsResolver.kt` — DNS fallback chain implementation (~140 lines)

### Modified Files
- `MainActivity.kt` — Added DOMAIN_FRONTING core, DNS integration (~200 lines added)
- `XrayBridge.kt` — Added xhttp transport case (~10 lines)
- `build.gradle.kts` — Version bump to 1.6.0

### Data Flow (Domain Fronting)
```
User pastes VLESS config → Domain list input
    ↓
extractDomainCandidates() → filters domains
    ↓
performFastScanProbe() → DnsResolver.resolveBlocking()
    ↓ (resolvedIp stored)
TCP connect to resolvedIp + TLS SNI=domain
    ↓
Top 100 → Final validation (same flow)
    ↓
Output: address=domain, sni=domain, host=template_host
```

---

## 🔒 Security Notes

**Domain Fronting is detectable** if ISP inspects:
- HTTP/2 `:authority` pseudo-header (may differ from SNI)
- TLS certificate CN/SAN (CDN cert won't match your subdomain)
- Traffic patterns (sustained high throughput to CDN domain)

**Recommended usage:**
- Use with popular CDN domains (high legitimacy)
- Rotate domains frequently
- Combine with other obfuscation (random padding, etc.)

---

## 📝 Known Limitations

1. **IPv4 only** — No IPv6 support
2. **Resume lost on app restart** — Pause/Resume only works in same session
3. **Single session scans** — No persistent scan history
4. **DoH bootstrap** — Uses direct IPs, bypasses circular DNS dependency
5. **Domain validation** — No DNSSEC verification (not needed for censorship bypass)

---

## 🚀 Quick Start (Domain Fronting)

1. Open CF L7 Scanner → Select "Domain Fronting Scanner" (green card)
2. Go to INPUT tab:
   - Paste your VLESS config (must have `host=your-cdn-subdomain.netlify.app`)
   - Set Custom DNS (optional, e.g., `8.8.8.8`)
   - Save
3. Go to DOMAIN FRT tab:
   - Paste domain list (one per line, e.g., `a16z.com`, `airbnb.com`)
   - Or load file (netlify-domains.txt, vercel-domains.txt, etc.)
   - Click START
4. Wait for scan completion (10-50 domains = 5-15 minutes)
5. Copy working configs → Import to NetMod/v2rayNG

---

## 🧪 Testing Checklist

Before release:
- [ ] Build APK in Android Studio
- [ ] Test Domain Fronting scan with 5-10 domains
- [ ] Verify DNS fallback logs in Logcat (`dns resolve domain=...`)
- [ ] Test xhttp config paste + scan
- [ ] Test Custom DNS field (save/load)
- [ ] Verify output VLESS link format (`address=domain`, not IP)
- [ ] Test pause/resume
- [ ] Test with NetMod import
- [ ] Verify Xray validation (if libv2ray.aar present)

---

## 📦 Upgrade Notes

**From v1.5.6:**
- New scanner core appears on home screen automatically
- Existing Cloudflare/CloudFront settings unchanged
- Domain Fronting uses separate settings profile (`app_settings_domain_fronting`)
- No migration needed

---

## 🙏 Credits

- **Original CF L7 Scanner:** Iranian developer
- **Domain Fronting + DNS fallback:** Added in v1.6.0
- **xray-core:** https://github.com/XTLS/Xray-core
- **AndroidLibXrayLite:** libv2ray.aar bridge

---

**Support:** GitHub Issues  
**License:** Private/Educational Use

---

**End of Release Notes**
