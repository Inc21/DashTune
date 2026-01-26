package com.example.dashtune.data.model

object StationTagHelper {
    private val genreMatchers = listOf(
        Regex("\\bpop\\b") to "Pop",
        Regex("\\brock\\b") to "Rock",
        Regex("classic\\s*rock") to "Classic Rock",
        Regex("hard\\s*rock") to "Hard Rock",
        Regex("soft\\s*rock") to "Soft Rock",
        Regex("classical|classic") to "Classical",
        Regex("\\bopera\\b") to "Opera",
        Regex("\\bbaroque\\b") to "Baroque",
        Regex("\\bromantic\\b") to "Romantic",
        Regex("\\bjazz\\b") to "Jazz",
        Regex("hip\\s*hop|hiphop|rap") to "Hip Hop",
        Regex("\\btrip\\s*hop\\b") to "Trip Hop",
        Regex("r\\s*&\\s*b|rnb") to "R&B",
        Regex("\\bsoul\\b") to "Soul",
        Regex("\\bfunk\\b") to "Funk",
        Regex("edm|electronic") to "Electronic",
        Regex("\\bdance\\b") to "Dance",
        Regex("\\bhouse\\b") to "House",
        Regex("deep\\s*house") to "Deep House",
        Regex("\\btechno\\b") to "Techno",
        Regex("\\btrance\\b") to "Trance",
        Regex("\\bdubstep\\b") to "Dubstep",
        Regex("drum\\s*&\\s*bass|drum\\s*and\\s*bass|dnb") to "Drum & Bass",
        Regex("\\bmetal\\b") to "Metal",
        Regex("heavy\\s*metal") to "Heavy Metal",
        Regex("black\\s*metal") to "Black Metal",
        Regex("death\\s*metal") to "Death Metal",
        Regex("metalcore") to "Metalcore",
        Regex("\\bpunk\\b") to "Punk",
        Regex("\\bgrunge\\b") to "Grunge",
        Regex("progressive|prog\\b") to "Progressive",
        Regex("\\bblues\\b") to "Blues",
        Regex("\\breggae\\b") to "Reggae",
        Regex("\\breggaeton\\b") to "Reggaeton",
        Regex("\\bska\\b") to "Ska",
        Regex("\\bsalsa\\b") to "Salsa",
        Regex("\\bmerengue\\b") to "Merengue",
        Regex("\\bflamenco\\b") to "Flamenco",
        Regex("\\bgospel\\b") to "Gospel",
        Regex("\\bchristian\\b") to "Christian",
        Regex("kids|children") to "Kids",
        Regex("\\bnews\\b") to "News",
        Regex("\\btalk\\b") to "Talk",
        Regex("\\bsports\\b") to "Sports",
        Regex("\\bindie\\b") to "Indie",
        Regex("alternative|alt\\b") to "Alternative",
        Regex("\\bcountry\\b") to "Country",
        Regex("\\bamericana\\b") to "Americana",
        Regex("\\bbluegrass\\b") to "Bluegrass",
        Regex("\\bfolk\\b") to "Folk",
        Regex("\\blatin\\b") to "Latin",
        Regex("\\bworld\\b") to "World",
        Regex("afrobeat|afrobeats|afro") to "Afrobeat",
        Regex("bollywood") to "Bollywood",
        Regex("\\bdesi\\b") to "Desi",
        Regex("\\bnew\\s*age\\b") to "New Age",
        Regex("\\bambient\\b") to "Ambient",
        Regex("chillout|chill|lofi|lo-fi") to "Chill",
        Regex("\\blounge\\b") to "Lounge",
        Regex("\\bdisco\\b") to "Disco",
        Regex("\\boldies\\b") to "Oldies",
        Regex("k\\s*-?pop") to "K-Pop"
    )

    fun extractGenres(tags: List<String>, maxGenres: Int = 2): List<String> {
        if (tags.isEmpty()) return emptyList()
        val cleanedTags = tags
            .flatMap { it.split(Regex("[,/;|]")) }
            .map { it.trim().lowercase() }
            .map { it.replace(Regex("[^a-z0-9+& ]"), " ") }
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() && it.length > 2 }

        val found = linkedSetOf<String>()
        cleanedTags.forEach { tag ->
            for ((matcher, label) in genreMatchers) {
                if (matcher.containsMatchIn(tag)) {
                    found.add(label)
                    break
                }
            }
        }

        // If no genre matched, use first clean tag as-is (capitalized)
        if (found.isEmpty() && cleanedTags.isNotEmpty()) {
            val firstTag = cleanedTags.first()
            if (firstTag.length <= 15) {
                found.add(firstTag.replaceFirstChar { it.uppercase() })
            }
        }

        return found.take(maxGenres)
    }

    fun matchKnownGenres(tags: List<String>, maxGenres: Int = 2): List<String> {
        if (tags.isEmpty()) return emptyList()
        val cleanedTags = tags
            .flatMap { it.split(Regex("[,/;|]")) }
            .map { it.trim().lowercase() }
            .map { it.replace(Regex("[^a-z0-9+& ]"), " ") }
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() && it.length > 2 }

        val found = linkedSetOf<String>()
        cleanedTags.forEach { tag ->
            for ((matcher, label) in genreMatchers) {
                if (matcher.containsMatchIn(tag)) {
                    found.add(label)
                    break
                }
            }
        }

        return found.take(maxGenres)
    }
}
