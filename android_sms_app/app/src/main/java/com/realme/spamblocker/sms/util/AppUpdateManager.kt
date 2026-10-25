package com.realme.spamblocker.sms.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String?,
    val releasePageUrl: String,
    val publishedAt: String
)

object AppUpdateManager {

    private const val GITHUB_LATEST_RELEASE_API =
        "https://api.github.com/repos/cmorpe7-ui/MorpsSMS/releases/latest"

    fun getCurrentVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    suspend fun checkForUpdate(context: Context): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        val currentVersion = getCurrentVersion(context)
        try {
            val url = URL(GITHUB_LATEST_RELEASE_API)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MorpsSMS-App")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                val tagName = json.optString("tag_name", "1.0.0")
                val releaseTitle = json.optString("name", tagName)
                val releaseNotes = json.optString("body", "No changelog provided.")
                val releasePageUrl = json.optString("html_url", "https://github.com/cmorpe7-ui/MorpsSMS/releases")
                val publishedAt = json.optString("published_at", "")

                // Find APK asset
                var downloadUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val assetName = asset.optString("name", "")
                        if (assetName.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                }

                val hasUpdate = isNewerVersion(tagName, currentVersion)

                Result.success(
                    AppUpdateInfo(
                        hasUpdate = hasUpdate,
                        latestVersion = tagName,
                        currentVersion = currentVersion,
                        releaseTitle = releaseTitle,
                        releaseNotes = releaseNotes,
                        downloadUrl = downloadUrl,
                        releasePageUrl = releasePageUrl,
                        publishedAt = publishedAt
                    )
                )
            } else {
                Result.failure(Exception("GitHub API returned HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isNewerVersion(latestTag: String, currentVersion: String): Boolean {
        val cleanLatest = latestTag.trim().removePrefix("v").removePrefix("V").split("-")[0]
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V").split("-")[0]

        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    fun startDownload(context: Context, downloadUrl: String, fileName: String = "MorpsSMS_Update.apk") {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(downloadUrl)
            val request = DownloadManager.Request(uri).apply {
                setTitle("MorpsSMS Update")
                setDescription("Downloading latest MorpsSMS APK...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setMimeType("application/vnd.android.package-archive")
            }
            downloadManager.enqueue(request)
            Toast.makeText(context, "Download started in background. Check your notification bar!", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            // Fallback: Launch in default browser
            openBrowser(context, downloadUrl)
        }
    }

    fun openBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open browser: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
