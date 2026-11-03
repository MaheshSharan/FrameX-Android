package com.framex.app.update

import android.content.Context
import com.framex.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

import java.security.MessageDigest

data class AppUpdateInfo(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val assetSize: Long,
    val isUpdateAvailable: Boolean,
    val sha256: String? = null
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class VerifyingSha(val apkFile: File) : DownloadState()
    data class Completed(val apkFile: File) : DownloadState()
    data class Failed(val error: String) : DownloadState()
}

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startDownload(downloadUrl: String, targetVersionName: String, expectedSha256: String?) {
        val cached = getCachedApkIfValid(targetVersionName, expectedSha256)
        if (cached != null) {
            _downloadState.value = DownloadState.Completed(cached)
            return
        }
        repositoryScope.launch {
            downloadUpdateApk(downloadUrl, targetVersionName, expectedSha256)
                .collect { state -> _downloadState.value = state }
        }
    }

    fun requestDownloadOrResume(info: AppUpdateInfo) {
        val cached = getCachedApkIfValid(info.versionName, info.sha256)
        if (cached != null) {
            _downloadState.value = DownloadState.Completed(cached)
        } else {
            startDownload(info.downloadUrl, info.versionName, info.sha256)
        }
    }

    fun resetDownloadState() {
        _downloadState.value = DownloadState.Idle
    }

    fun getCachedApkIfValid(targetVersionName: String, expectedSha256: String?): File? {
        val updatesDir = File(context.cacheDir, "updates")
        val apkFile = File(updatesDir, "FrameX_v$targetVersionName.apk")
        if (!apkFile.exists()) return null
        if (expectedSha256.isNullOrBlank()) return apkFile
        val actualHash = computeSha256(apkFile)
        if (!actualHash.equals(expectedSha256, ignoreCase = true)) {
            apkFile.delete()
            return null
        }
        return apkFile
    }

    fun cleanupStaleUpdateApks() {
        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) return
        val currentVersion = BuildConfig.VERSION_NAME
        updatesDir.listFiles { file -> file.name.startsWith("FrameX_v") && file.name.endsWith(".apk") }
            ?.forEach { file ->
                val fileVersion = file.name.removePrefix("FrameX_v").removeSuffix(".apk")
                if (!isVersionHigher(fileVersion, currentVersion)) {
                    file.delete()
                }
            }
    }

    private companion object {
        const val GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/MaheshSharan/FrameX-Android/releases/latest"
        const val CONNECT_TIMEOUT_MS = 5000
        const val READ_TIMEOUT_MS = 8000
        private val SHA256_REGEX = Regex("(?i)sha-?256\\s*:?\\s*([a-f0-9]{64})")
    }

    suspend fun checkForUpdates(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(GITHUB_LATEST_RELEASE_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "FrameX-App-${BuildConfig.VERSION_NAME}")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                return@withContext Result.failure(Exception("No releases found on GitHub repository"))
            } else if (responseCode == 403) {
                return@withContext Result.failure(Exception("GitHub API rate limit exceeded. Try again later."))
            } else if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("Server returned HTTP $responseCode"))
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)

            val tagName = json.optString("tag_name", "").removePrefix("v")
            val releaseNotes = json.optString("body", "No release notes provided.")
            
            val extractedSha256 = SHA256_REGEX.find(releaseNotes)?.groupValues?.get(1)?.lowercase()

            var downloadUrl = ""
            var assetSize = 0L

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        assetSize = asset.optLong("size", 0L)
                        if (name.contains("release", ignoreCase = true)) {
                            break
                        }
                    }
                }
            }

            if (downloadUrl.isBlank()) {
                return@withContext Result.failure(Exception("No APK asset attached to the latest release"))
            }

            val isAvailable = VersionCompare.isHigher(tagName, BuildConfig.VERSION_NAME)
            Result.success(
                AppUpdateInfo(
                    versionName = tagName,
                    releaseNotes = releaseNotes,
                    downloadUrl = downloadUrl,
                    assetSize = assetSize,
                    isUpdateAvailable = isAvailable,
                    sha256 = extractedSha256
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    fun downloadUpdateApk(downloadUrl: String, targetVersionName: String, expectedSha256: String? = null): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0f, 0L, 0L))
        var connection: HttpURLConnection? = null
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }
            val apkFile = File(updatesDir, "FrameX_v$targetVersionName.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val url = URL(downloadUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "FrameX-App-${BuildConfig.VERSION_NAME}")
            }

            val currentJob = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
            val cancelHandler = currentJob?.invokeOnCompletion {
                runCatching { connection.disconnect() }
            }

            try {
                val totalBytes = connection.contentLengthLong
                var bytesDownloaded = 0L

                connection.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead = 0
                        var lastReportedProgress = 0f

                        while (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive != false &&
                            input.read(buffer).also { bytesRead = it } != -1
                        ) {
                            output.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead
                            val progress = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
                            
                            if (progress - lastReportedProgress >= 0.02f || progress == 1.0f) {
                                lastReportedProgress = progress
                                emit(DownloadState.Downloading(progress, bytesDownloaded, totalBytes))
                            }
                        }
                    }
                }

                if (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive == false) {
                    apkFile.delete()
                    emit(DownloadState.Failed("Download cancelled"))
                    return@flow
                }

                if (totalBytes > 0 && apkFile.length() != totalBytes) {
                    apkFile.delete()
                    emit(DownloadState.Failed("Downloaded file size mismatch"))
                    return@flow
                }

                emit(DownloadState.VerifyingSha(apkFile))
                kotlinx.coroutines.delay(400)
                val computedHash = computeSha256(apkFile)
                if (!expectedSha256.isNullOrBlank()) {
                    if (!computedHash.equals(expectedSha256, ignoreCase = true)) {
                        apkFile.delete()
                        emit(DownloadState.Failed("SHA-256 checksum mismatch (expected $expectedSha256, got $computedHash)"))
                        return@flow
                    }
                }

                emit(DownloadState.Completed(apkFile))
            } finally {
                cancelHandler?.dispose()
            }
        } catch (e: Exception) {
            if (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive == false) {
                emit(DownloadState.Failed("Download cancelled"))
            } else {
                emit(DownloadState.Failed(e.localizedMessage ?: "Download failed"))
            }
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
