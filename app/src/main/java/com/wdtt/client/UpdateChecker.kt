package com.wdtt.client

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Проверка новых версий через GitHub Releases API и установка APK.
 *
 * Релиз берётся из репозитория [REPO] (endpoint /releases/latest).
 * Приложение подбирает APK под ABI устройства (Build.SUPPORTED_ABIS) —
 * предпочитается точное имя qWDTT-<abi>.apk, затем qWDTT-universal.apk,
 * затем совпадение по ABI в любом имени ассета.
 */
object UpdateChecker {

    private const val REPO = "jewbsv/proxy-turn-vk-android"
    private const val RELEASES_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val TAG = "UpdateChecker"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Информация о релизе: версия, заголовок, заметки, ссылка и список APK-ассетов. */
    data class ReleaseInfo(
        val version: String,
        val releaseName: String,
        val body: String,
        val releaseUrl: String,
        val assets: List<Pair<String, String>>,
    ) {
        /** APK под устройство: (имя файла, url) или null, если подходящего ассета нет. */
        fun assetForDevice(): Pair<String, String>? {
            val abi = Build.SUPPORTED_ABIS.firstOrNull { it.isNotEmpty() }
            abi?.let {
                assets.firstOrNull { (name, _) -> name == "qWDTT-$it.apk" }?.let { a -> return a }
            }
            assets.firstOrNull { (name, _) -> name == "qWDTT-universal.apk" }?.let { a -> return a }
            abi?.let {
                assets.firstOrNull { (name, _) ->
                    name.startsWith("app-") && name.contains(it) && name.endsWith(".apk")
                }?.let { a -> return a }
            }
            return assets.firstOrNull { (name, _) -> name.endsWith(".apk") }
        }
    }

    /**
     * Сравнение версий: >0 если [a] новее [b], 0 если равны, <0 если старше.
     * Снимает префикс "v", отбрасывает суффикс после "-" (например "v1.4.0-noads" -> 1.4.0).
     */
    fun compareVersions(a: String, b: String): Int {
        val pa = parseVersion(a)
        val pb = parseVersion(b)
        val max = maxOf(pa.size, pb.size)
        for (i in 0 until max) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun parseVersion(s: String): List<Int> {
        val clean = s.trim()
            .removePrefix("v")
            .substringBefore("-")
            .substringBefore("_")
            .trim()
        return clean.split('.').mapNotNull { it.toIntOrNull() }
    }

    /**
     * Запрос к GitHub Releases API. Возвращает [ReleaseInfo] последнего релиза.
     *
     * При [onlyIfNewer] == true возвращает null, если найденная версия не новее
     * текущей (BuildConfig.VERSION_NAME) — для автоматической проверки при запуске.
     * При [onlyIfNewer] == false возвращает информацию о последнем релизе в любом
     * случае (null только при ошибке сети/API) — для ручной кнопки «Проверить обновления».
     */
    suspend fun fetchLatestRelease(onlyIfNewer: Boolean = true): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "qWDTT-Android")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GitHub API error: ${response.code}")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val tag = json.optString("tag_name", "").ifEmpty { return@withContext null }
                val releaseName = json.optString("name", tag)
                val releaseBody = json.optString("body", "")
                val releaseUrl = json.optString("html_url", "")
                val assets = json.optJSONArray("assets")?.let { arr ->
                    buildList {
                        for (i in 0 until arr.length()) {
                            val a = arr.getJSONObject(i)
                            val assetName = a.optString("name", "")
                            val assetUrl = a.optString("browser_download_url", "")
                            if (assetName.endsWith(".apk") && assetUrl.isNotEmpty()) {
                                add(assetName to assetUrl)
                            }
                        }
                    }
                } ?: emptyList()

                val info = ReleaseInfo(tag, releaseName, releaseBody, releaseUrl, assets)
                val current = BuildConfig.VERSION_NAME
                val hasUpdate = compareVersions(info.version, current) > 0
                Log.i(TAG, "latest=${info.version} current=$current update=$hasUpdate apks=${assets.size}")
                if (onlyIfNewer && !hasUpdate) null else info
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
            null
        }
    }

    /**
     * Фоновое скачивание APK через DownloadManager в публичную папку
     * Environment.DIRECTORY_DOWNLOADS. По завершении загрузки файл открывается
     * системным установщиком (или пользователь тапает по уведомлению).
     *
     * Используется прямая ссылка на APK (browser_download_url из assets),
     * а не html_url страницы релиза — иначе DownloadManager качал бы HTML.
     */
    fun downloadAndInstall(context: Context, info: ReleaseInfo) {
        val asset = info.assetForDevice()
            ?: throw IllegalArgumentException("В релизе ${info.version} нет APK-ассета")
        val (assetName, assetUrl) = asset

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: throw IllegalStateException("DownloadManager недоступен")

        val target = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            assetName
        )
        runCatching { if (target.exists()) target.delete() }

        val request = DownloadManager.Request(Uri.parse(assetUrl)).apply {
            setTitle("qWDTT ${info.version}")
            setDescription("Скачивание обновления…")
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, assetName)
        }
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (c == null || i?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
                runCatching { c.unregisterReceiver(this) }
                if (isDownloadSuccess(dm, downloadId)) {
                    launchInstaller(c, target)
                } else {
                    Log.w(TAG, "Download failed: id=$downloadId")
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
        Log.i(TAG, "Download started: $assetName -> $target")
    }

    private fun isDownloadSuccess(dm: DownloadManager, id: Long): Boolean {
        val query = DownloadManager.Query().setFilterById(id)
        dm.query(query).use { c ->
            if (c.moveToFirst()) {
                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                return status == DownloadManager.STATUS_SUCCESSFUL
            }
        }
        return false
    }

    private fun launchInstaller(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Launch installer failed: ${e.message}")
        }
    }
}
