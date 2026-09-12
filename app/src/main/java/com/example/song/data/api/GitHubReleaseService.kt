package com.example.song.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path

data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("content_type") val contentType: String?,
    @SerializedName("size") val size: Long,
    @SerializedName("browser_download_url") val downloadUrl: String
) {
    val isApk: Boolean
        get() = name.endsWith(".apk", ignoreCase = true) ||
                contentType?.contains("android.package-archive", ignoreCase = true) == true
}

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String?,
    @SerializedName("body") val body: String?,
    @SerializedName("draft") val draft: Boolean = false,
    @SerializedName("prerelease") val prerelease: Boolean = false,
    @SerializedName("published_at") val publishedAt: String?,
    @SerializedName("assets") val assets: List<GitHubAsset> = emptyList()
) {
    val apkAsset: GitHubAsset?
        get() = assets.find { it.isApk }
}

interface GitHubReleaseService {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRelease
}
