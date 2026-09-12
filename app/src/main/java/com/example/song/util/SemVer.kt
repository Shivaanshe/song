package com.example.song.util

/**
 * Robust Semantic Versioning (SemVer) parser and comparator.
 *
 * Strips leading 'v' or 'V' prefixes and compares Major.Minor.Patch components
 * sequentially as integers to avoid evaluation errors (e.g., evaluating 3.10.0 as smaller than 3.4.0).
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val rawVersion: String = "$major.$minor.$patch"
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (this.major != other.major) {
            return this.major.compareTo(other.major)
        }
        if (this.minor != other.minor) {
            return this.minor.compareTo(other.minor)
        }
        return this.patch.compareTo(other.patch)
    }

    fun isNewerThan(other: SemVer): Boolean = this > other

    companion object {
        /**
         * Parses a version string into a [SemVer] object.
         * Examples:
         * "v3.10.0" -> SemVer(3, 10, 0)
         * "3.4" -> SemVer(3, 4, 0)
         * "V1.0.0-rc1" -> SemVer(1, 0, 0)
         */
        fun parse(versionString: String?): SemVer? {
            if (versionString.isNullOrBlank()) return null
            val clean = versionString.trim()
                .removePrefix("v")
                .removePrefix("V")
                .trim()

            if (clean.isEmpty()) return null

            // Strip pre-release suffixes (e.g., -alpha, -rc1, +build)
            val baseVersion = clean.split("-")[0].split("+")[0].trim()
            val parts = baseVersion.split(".")

            val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0

            return SemVer(
                major = major,
                minor = minor,
                patch = patch,
                rawVersion = versionString
            )
        }
    }
}
