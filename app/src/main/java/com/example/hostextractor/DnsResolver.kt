package com.example.hostextractor

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

object DnsResolver {

    // Known IPs injected by Iranian ISPs for censored domains
    private val POISON_IPS = setOf(
        "10.10.34.34", "10.10.34.35", "10.10.34.36",
        "78.39.198.34", "78.39.198.35",
        "85.15.9.138", "85.15.9.139",
        "10.10.0.1", "10.10.0.2",
        "127.0.0.1", "0.0.0.0",
        "192.168.1.1", "192.168.0.1", "10.0.0.1",  // Common router IPs (not real CDN IPs)
        "172.16.0.1",  // Private network
    )

    // All use direct IPs — no DNS needed to reach them, bypassing any DoH bootstrap issue
    private val DOH_PROVIDERS = listOf(
        "https://1.1.1.1/dns-query",
        "https://9.9.9.9/dns-query",
        "https://149.112.112.112/dns-query"
    )

    // Compiled regex for performance (avoid recompiling on each call)
    private val DOMAIN_REGEX = Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$")
    private val IP_REGEX = Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")

    /**
     * Resolve a domain to an IPv4 address, bypassing DNS poisoning.
     *
     * @param domain Domain name to resolve
     * @param customDnsServers Comma or newline-separated list of DNS IPs (e.g., "8.8.8.8,1.1.1.1" or "8.8.8.8\n1.1.1.1")
     * @param dnsMode Resolution mode:
     *   - "fallback": custom DNS → system DNS → DoH (default, may give false positives)
     *   - "custom_only": only custom DNS, no fallback (strict)
     *   - "system_only": only system DNS (realistic phone behavior)
     *
     * Thread-safe. Blocks for up to ~15 seconds in worst case.
     */
    fun resolveBlocking(domain: String, customDnsServers: String = "", dnsMode: String = "fallback"): String? {
        // Validate domain first (prevent crashes from bad input)
        val cleanDomain = domain.trim().lowercase()
        if (!isValidDomain(cleanDomain)) {
            ScannerLog.scan("dns resolve INVALID domain=$domain")
            return null
        }

        // Parse multiple DNS servers (comma or newline separated)
        val dnsServers = customDnsServers
            .split(',', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() && isValidIpv4(it) }

        // Try custom DNS servers if provided
        if (dnsServers.isNotEmpty() && dnsMode != "system_only") {
            for (dnsServer in dnsServers) {
                resolveViaUdpDns(cleanDomain, dnsServer)?.let {
                    ScannerLog.scan("dns resolve SUCCESS method=UDP server=$dnsServer domain=$cleanDomain ip=$it")
                    return it
                }
            }
            ScannerLog.scan("dns resolve FAIL all custom DNS servers domain=$cleanDomain")

            // If custom_only mode, stop here
            if (dnsMode == "custom_only") {
                ScannerLog.scan("dns resolve FAILED domain=$cleanDomain (custom_only mode, no fallback)")
                return null
            }
        }

        // Try system DNS with poison check (unless custom_only mode)
        if (dnsMode != "custom_only") {
            resolveViaSystem(cleanDomain)?.let {
                ScannerLog.scan("dns resolve SUCCESS method=System domain=$cleanDomain ip=$it")
                return it
            }
        }

        // Fallback to DoH providers (only in fallback mode)
        if (dnsMode == "fallback") {
            for (provider in DOH_PROVIDERS) {
                resolveViaDoH(cleanDomain, provider)?.let {
                    ScannerLog.scan("dns resolve SUCCESS method=DoH domain=$cleanDomain ip=$it provider=$provider")
                    return it
                }
            }
        }

        ScannerLog.scan("dns resolve FAILED domain=$cleanDomain mode=$dnsMode")
        return null
    }

    /**
     * Validate domain format: RFC 1035 compliance + length checks.
     * Prevents crashes from malformed domains.
     */
    private fun isValidDomain(domain: String): Boolean {
        if (domain.isBlank() || domain.length > 253) return false
        if (domain.startsWith(".") || domain.endsWith(".")) return false
        if (domain.contains("..")) return false
        if (!DOMAIN_REGEX.matches(domain)) return false
        // Check each label length (max 63 per RFC 1035)
        return domain.split(".").all { it.length in 1..63 }
    }

    private fun resolveViaSystem(domain: String): String? = try {
        InetAddress.getAllByName(domain)
            .filter { it.address.size == 4 }
            .mapNotNull { it.hostAddress }
            .firstOrNull { it.isNotBlank() && it !in POISON_IPS }
    } catch (_: Throwable) { null }

    private fun resolveViaDoH(domain: String, providerUrl: String): String? {
        return try {
        val encoded = URLEncoder.encode(domain, "UTF-8")
        val url = URL("$providerUrl?name=$encoded&type=A")
        val conn = url.openConnection() as HttpsURLConnection
        try {
            conn.setRequestProperty("Accept", "application/dns-json")
            conn.setRequestProperty("User-Agent", "CF-L7-Scanner/1.6.0")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.instanceFollowRedirects = false  // Prevent redirect attacks

            // Read response safely with try-finally on stream
            val response = conn.inputStream.bufferedReader().use { it.readText() }

            // Validate HTTP status
            if (conn.responseCode != 200) {
                ScannerLog.scan("dns DoH HTTP error provider=$providerUrl code=${conn.responseCode}")
                return null
            }

            val answers = JSONObject(response).optJSONArray("Answer") ?: return null
            (0 until answers.length()).asSequence()
                .mapNotNull { answers.optJSONObject(it) }
                .filter { it.optInt("type") == 1 }  // A record only
                .map { it.optString("data", "") }
                .firstOrNull { ip -> ip.isNotBlank() && isValidIpv4(ip) && ip !in POISON_IPS }
        } finally {
            conn.disconnect()  // ✅ Always close connection
        }
        } catch (e: Throwable) {
            ScannerLog.scan("dns DoH exception provider=$providerUrl error=${e.message}")
            null
        }
    }

    private fun resolveViaUdpDns(domain: String, server: String, port: Int = 53): String? {
        return try {
        val query = buildDnsQuery(domain)
        if (query.isEmpty()) return null  // Invalid domain

        DatagramSocket().use { socket ->  // ✅ Auto-close on exception
            socket.soTimeout = 3000
            socket.send(DatagramPacket(query, query.size, InetAddress.getByName(server), port))
            val buf = ByteArray(512)
            val reply = DatagramPacket(buf, buf.size)
            socket.receive(reply)
            parseDnsARecord(buf, reply.length)?.takeIf { it !in POISON_IPS }
        }
        } catch (e: Throwable) {
            ScannerLog.scan("dns UDP exception server=$server error=${e.message}")
            null
        }
    }

    private fun buildDnsQuery(domain: String): ByteArray {
        val out = mutableListOf<Byte>()
        fun b(v: Int) { out.add((v and 0xFF).toByte()) }
        fun s(v: Int) { b(v shr 8); b(v) }

        s(0x1234)            // Transaction ID
        s(0x0100)            // Flags: standard query + recursion desired
        s(1); s(0); s(0); s(0) // 1 question, 0 answers/authority/additional

        // Encode domain labels with validation
        val labels = domain.split(".")
        for (label in labels) {
            if (label.isEmpty() || label.length > 63) {
                ScannerLog.scan("dns buildQuery INVALID label length=${label.length}")
                return byteArrayOf()  // Return empty = invalid
            }
            b(label.length)
            label.forEach { c ->
                if (c.code > 255) {
                    ScannerLog.scan("dns buildQuery INVALID non-ASCII char=$c")
                    return byteArrayOf()
                }
                b(c.code)
            }
        }

        b(0)      // Root label
        s(1)      // Type=A
        s(1)      // Class=IN
        return out.toByteArray()
    }

    private fun parseDnsARecord(buf: ByteArray, length: Int): String? {
        try {
            if (length < 12) return null
            val answerCount = ((buf[6].toInt() and 0xFF) shl 8) or (buf[7].toInt() and 0xFF)
            if (answerCount == 0 || answerCount > 20) return null  // Sanity check

            var i = 12
            // Skip question domain name with bounds checking
            while (i < length) {
                val len = buf[i].toInt() and 0xFF
                if (len == 0) { i++; break }
                if (len and 0xC0 == 0xC0) { i += 2; break }  // Compression pointer
                if (len > 63) return null  // Invalid label length
                if (i + len + 1 > length) return null  // Bounds check
                i += len + 1
            }
            if (i + 4 > length) return null
            i += 4  // skip QTYPE + QCLASS

            // Parse answer section
            for (a in 0 until answerCount) {
                if (i >= length) return null

                // Skip answer name with bounds checking
                if (i < length && buf[i].toInt() and 0xC0 == 0xC0) {
                    i += 2  // Compression pointer
                } else {
                    while (i < length) {
                        val len = buf[i].toInt() and 0xFF
                        if (len == 0) { i++; break }
                        if (len > 63) return null  // Invalid label length
                        if (i + len + 1 > length) return null  // Bounds check
                        i += len + 1
                    }
                }

                if (i + 10 > length) return null
                val type  = ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
                val rdLen = ((buf[i + 8].toInt() and 0xFF) shl 8) or (buf[i + 9].toInt() and 0xFF)

                if (rdLen > 512) return null  // Sanity check

                i += 10

                // Found A record with correct length
                if (type == 1 && rdLen == 4 && i + 4 <= length) {
                    val ip = buildString {
                        append(buf[i].toInt() and 0xFF)
                        append('.')
                        append(buf[i + 1].toInt() and 0xFF)
                        append('.')
                        append(buf[i + 2].toInt() and 0xFF)
                        append('.')
                        append(buf[i + 3].toInt() and 0xFF)
                    }
                    return ip
                }

                if (i + rdLen > length) return null  // Bounds check
                i += rdLen
            }
            return null
        } catch (e: Throwable) {
            ScannerLog.scan("dns parseARecord exception error=${e.message}")
            return null
        }
    }

    private fun isValidIpv4(value: String): Boolean =
        Regex("""^(\d{1,3}\.){3}\d{1,3}$""").matches(value)
}
