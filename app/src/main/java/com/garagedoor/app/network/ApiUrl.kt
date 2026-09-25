package com.garagedoor.app.network

import com.garagedoor.app.data.AppSettings
import java.net.URI

object ApiUrl {

    /** Normalize saved API URL — public hosts must use HTTPS on Android 9+. */
    fun normalize(raw: String): String {
        var url = raw.trim().removeSuffix("/")
        if (url.isBlank()) return AppSettings.DEFAULT_API_URL

        if (!url.contains("://")) {
            url = "https://$url"
        }

        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        val host = uri.host ?: return url
        val isLocal = isLocalHost(host)

        return when {
            isLocal -> {
                if (uri.scheme != "http") {
                    URI("http", uri.userInfo, host, uri.port, uri.path, uri.query, uri.fragment).toString()
                        .removeSuffix("/")
                } else {
                    url
                }
            }
            uri.scheme == "http" -> url.replaceFirst("http://", "https://")
            else -> url
        }
    }

    private fun isLocalHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".local")) return true
        if (host.startsWith("10.")) return true
        if (host.startsWith("192.168.")) return true
        if (host.startsWith("172.")) {
            val second = host.split('.').getOrNull(1)?.toIntOrNull() ?: return false
            if (second in 16..31) return true
        }
        return false
    }
}
