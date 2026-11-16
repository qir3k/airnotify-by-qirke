package com.qirke.airnotify.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.qirke.airnotify.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val hasUpdate: Boolean
)

object InAppUpdater {

    private const val GITHUB_REPO = "qirke/airnotify-by-qirke"
    private const val RELEASES_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Checks the GitHub Releases API for a newer version than current BuildConfig.VERSION_NAME.
     */
    suspend fun checkForUpdate(
        customRepo: String = GITHUB_REPO,
        currentVersion: String = BuildConfig.VERSION_NAME
    ): UpdateInfo = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$customRepo/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            val body = response.body?.string().orEmpty()
            if (code == 404) {
                // No release published yet on this repo
                return@withContext UpdateInfo(
                    tagName = "v$currentVersion",
                    versionName = currentVersion,
                    releaseTitle = "No Releases Found",
                    releaseNotes = "No GitHub releases have been published yet for $customRepo.",
                    downloadUrl = "",
                    hasUpdate = false
                )
            }
            throw IllegalStateException("GitHub API returned error $code: $body")
        }

        val jsonStr = response.body?.string() ?: throw IllegalStateException("Empty response from GitHub")
        val json = JSONObject(jsonStr)

        val tagName = json.optString("tag_name", "")
        val releaseTitle = json.optString("name", tagName)
        val releaseNotes = json.optString("body", "No release notes provided.")
        val cleanVersion = tagName.removePrefix("v").trim()

        var downloadUrl = ""
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val assetName = asset.optString("name", "")
                if (assetName.endsWith(".apk", ignoreCase = true)) {
                    downloadUrl = asset.optString("browser_download_url", "")
                    break
                }
            }
        }

        val hasNewerVersion = isNewer(cleanVersion, currentVersion)

        return@withContext UpdateInfo(
            tagName = tagName,
            versionName = cleanVersion,
            releaseTitle = releaseTitle,
            releaseNotes = releaseNotes,
            downloadUrl = downloadUrl,
            hasUpdate = hasNewerVersion && downloadUrl.isNotBlank()
        )
    }

    /**
     * Downloads the APK file and launches the package installer.
     */
    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        versionName: String,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val destinationDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(destinationDir, "AirNotify-v$versionName.apk")

        if (apkFile.exists()) {
            apkFile.delete()
        }

        val request = Request.Builder().url(downloadUrl).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("Download failed with HTTP ${response.code}")
        }

        val body = response.body ?: throw IllegalStateException("Empty download body")
        val contentLength = body.contentLength()
        val inputStream = body.byteStream()
        val outputStream = FileOutputStream(apkFile)

        val buffer = ByteArray(8 * 1024)
        var bytesRead: Int
        var totalBytesRead = 0L

        outputStream.use { out ->
            inputStream.use { input ->
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0) {
                        val progress = ((totalBytesRead * 100) / contentLength).toInt()
                        onProgress(progress)
                    }
                }
            }
        }

        return@withContext apkFile
    }

    /**
     * Prompts the system to install the downloaded APK file.
     */
    fun launchInstaller(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val manageIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(manageIntent)
                return
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(installIntent)
        Timber.i("Launched package installer for %s", apkFile.name)
    }

    /**
     * Compares version strings (e.g. "1.1.0" > "1.0.0").
     */
    private fun isNewer(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val length = maxOf(latestParts.size, currentParts.size)

            for (i in 0 until length) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
        } catch (_: Exception) {}
        return false
    }
}
