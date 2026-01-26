package com.example.dashtune.data.model

import java.net.URI

object StationIconHelper {
    fun resolveImageUrl(rawImageUrl: String, websiteUrl: String): String {
        val cleaned = rawImageUrl.trim()
        if (cleaned.isBlank() || cleaned.equals("null", ignoreCase = true)) {
            return buildFaviconUrl(websiteUrl)
        }

        val normalized = normalizeImageUrl(cleaned)
        return normalized.ifBlank { buildFaviconUrl(websiteUrl) }
    }

    fun normalizeImageUrl(rawImageUrl: String): String {
        val cleaned = rawImageUrl.trim()
        if (cleaned.isBlank() || cleaned.equals("null", ignoreCase = true)) {
            return ""
        }

        val normalized = when {
            cleaned.startsWith("https://") -> cleaned
            cleaned.startsWith("http://") -> "https://${cleaned.removePrefix("http://")}" 
            cleaned.startsWith("//") -> "https:$cleaned"
            else -> "https://$cleaned"
        }

        return normalized.takeIf { it.length > "https://".length } ?: ""
    }

    fun buildFaviconUrl(website: String): String {
        val domain = extractDomain(website) ?: return ""
        return "https://www.google.com/s2/favicons?domain=$domain&sz=128"
    }

    fun normalizeWebsiteUrl(website: String): String {
        val trimmed = website.trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun extractDomain(website: String): String? {
        if (website.isBlank()) return null
        val trimmed = website.trim()
        val normalized = normalizeWebsiteUrl(trimmed)

        val host = try {
            URI(normalized).host
        } catch (e: Exception) {
            null
        }

        val rawDomain = host ?: trimmed.substringBefore("/").substringBefore("?").substringBefore("#")
        val cleaned = if (rawDomain.startsWith("www.")) rawDomain.substring(4) else rawDomain
        return cleaned.takeIf { it.isNotBlank() }
    }
}
