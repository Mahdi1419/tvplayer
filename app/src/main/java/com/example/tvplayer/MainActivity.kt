package com.example.tvplayer

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : AppCompatActivity() {

    // ← آدرس پیش‌فرض فایل txt حاوی لینک‌های ویدیو را اینجا تنظیم کنید
    private var listUrl = "https://example.com/videos.txt"

    private lateinit var recyclerView: RecyclerView
    private lateinit var txtStatus: TextView
    private lateinit var txtEmpty: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnTabOnline: MaterialButton
    private lateinit var btnTabLocal: MaterialButton
    private lateinit var adapter: VideoAdapter
    private lateinit var httpClient: OkHttpClient

    private var onlineItems = listOf<VideoItem>()
    private var localItems = listOf<VideoItem>()
    private var currentTab = Tab.ONLINE
    private val usbTreeUris = mutableListOf<Uri>()

    private val openUsbFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            }
            if (usbTreeUris.none { it.toString() == uri.toString() }) usbTreeUris.add(uri)
            scanLocalVideos()
        }
    }

    private enum class Tab { ONLINE, LOCAL }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scanLocalVideos()
        } else {
            txtStatus.text = getString(R.string.permission_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // TV stays landscape; phones may rotate freely, including portrait.
        requestedOrientation = if (NetworkClient.isTv(this)) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        httpClient = NetworkClient.create(this)

        recyclerView = findViewById(R.id.recyclerView)
        txtStatus = findViewById(R.id.txtStatus)
        txtEmpty = findViewById(R.id.txtEmpty)
        progressBar = findViewById(R.id.progressBar)
        btnTabOnline = findViewById(R.id.btnTabOnline)
        btnTabLocal = findViewById(R.id.btnTabLocal)

        adapter = VideoAdapter { item -> openPlayer(item.url) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        btnTabOnline.setOnClickListener { switchTab(Tab.ONLINE) }
        btnTabLocal.setOnClickListener { switchTab(Tab.LOCAL) }

        findViewById<MaterialButton>(R.id.btnSettings).setOnClickListener { showSettingsDialog() }
        findViewById<MaterialButton>(R.id.btnAddLink).setOnClickListener { showAddLinkDialog() }
        findViewById<MaterialButton>(R.id.btnUsb).setOnClickListener { openUsbFolderLauncher.launch(null) }
        loadPersistedUsbUris()

        switchTab(Tab.ONLINE)
        loadListFromUrl(listUrl)
    }

    // ---------- تب‌ها ----------

    private fun switchTab(tab: Tab) {
        currentTab = tab
        updateTabStyles()
        renderCurrentTab()

        if (tab == Tab.LOCAL && localItems.isEmpty()) {
            ensurePermissionAndScan()
        }
    }

    private fun updateTabStyles() {
        val selectedBg = ContextCompat.getColor(this, R.color.accent)
        val unselectedBg = ContextCompat.getColor(this, R.color.surface)
        val selectedText = ContextCompat.getColor(this, R.color.text_primary)
        val unselectedText = ContextCompat.getColor(this, R.color.text_secondary)

        val onlineSelected = currentTab == Tab.ONLINE
        btnTabOnline.backgroundTintList =
            ColorStateList.valueOf(if (onlineSelected) selectedBg else unselectedBg)
        btnTabOnline.setTextColor(if (onlineSelected) selectedText else unselectedText)

        val localSelected = currentTab == Tab.LOCAL
        btnTabLocal.backgroundTintList =
            ColorStateList.valueOf(if (localSelected) selectedBg else unselectedBg)
        btnTabLocal.setTextColor(if (localSelected) selectedText else unselectedText)
    }

    private fun renderCurrentTab() {
        val list = if (currentTab == Tab.ONLINE) onlineItems else localItems
        adapter.submitList(list)

        val isEmpty = list.isEmpty()
        txtEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        txtEmpty.text = if (currentTab == Tab.ONLINE)
            getString(R.string.empty_online) else getString(R.string.empty_local)
    }

    // ---------- ویدیوهای محلی ----------

    private fun ensurePermissionAndScan() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_VIDEO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            scanLocalVideos()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun scanLocalVideos() {
        progressBar.visibility = View.VISIBLE
        txtStatus.text = "در حال جستجوی ویدیوهای دستگاه..."

        Thread {
            val result = LocalVideoScanner.scan(this, usbTreeUris)
            Handler(Looper.getMainLooper()).post {
                localItems = result
                progressBar.visibility = View.GONE
                txtStatus.text = "${result.size} ویدیو روی دستگاه پیدا شد"
                if (currentTab == Tab.LOCAL) renderCurrentTab()

                // Some Android TV firmware does not expose USB media through MediaStore.
                // If a removable volume exists and no folder permission was saved, ask once
                // for access through the system document picker.
                if (currentTab == Tab.LOCAL && result.isEmpty() && usbTreeUris.isEmpty() && hasRemovableStorage()) {
                    openUsbFolderLauncher.launch(null)
                }
            }
        }.start()
    }

    private fun hasRemovableStorage(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val storageManager = getSystemService(android.os.storage.StorageManager::class.java)
            storageManager.storageVolumes.any { it.isRemovable }
        } catch (_: Exception) {
            false
        }
    }

    private fun loadPersistedUsbUris() {
        usbTreeUris.clear()
        contentResolver.persistedUriPermissions.forEach { permission ->
            if ((permission.modeFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                usbTreeUris.add(permission.uri)
            }
        }
    }

    // ---------- لیست آنلاین ----------

    private fun openPlayer(url: String) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra(PlayerActivity.EXTRA_URL, url)
        startActivity(intent)
    }

    private fun loadListFromUrl(urlString: String) {
        progressBar.visibility = View.VISIBLE
        txtStatus.text = "در حال دریافت لیست..."

        Thread {
            try {
                val text = downloadText(urlString)
                val parsed = parseVideoList(text)
                Handler(Looper.getMainLooper()).post {
                    onlineItems = parsed
                    progressBar.visibility = View.GONE
                    txtStatus.text = if (parsed.isEmpty())
                        "لینکی در فایل پیدا نشد"
                    else
                        "${parsed.size} لینک بارگیری شد"
                    if (currentTab == Tab.ONLINE) renderCurrentTab()
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    progressBar.visibility = View.GONE
                    txtStatus.text = "خطا در دریافت لیست: ${e.message}"
                }
            }
        }.start()
    }

    private fun downloadText(urlString: String): String {
        val request = Request.Builder()
            .url(urlString)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}")
            }
            return response.body?.string() ?: ""
        }
    }

    // هر خط از فایل txt می‌تواند یکی از این دو شکل باشد:
    //   http://example.com/video1.mp4
    //   عنوان دلخواه ویدیو, http://example.com/video1.mp4
    private fun parseVideoList(text: String): List<VideoItem> {
        val result = mutableListOf<VideoItem>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach

            val commaIndex = line.indexOf(',')
            if (commaIndex > 0 && line.substring(commaIndex + 1).trim().startsWith("http")) {
                val title = line.substring(0, commaIndex).trim()
                val url = line.substring(commaIndex + 1).trim()
                result.add(VideoItem(title.ifEmpty { url }, url))
            } else if (line.startsWith("http")) {
                result.add(VideoItem(line, line))
            }
        }
        return result
    }

    // ---------- دیالوگ‌ها ----------

    private fun showSettingsDialog() {
        val input = EditText(this)
        input.setText(listUrl)
        input.hint = getString(R.string.hint_list_url)
        input.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        input.setHintTextColor(ContextCompat.getColor(this, R.color.text_secondary))

        val container = FrameLayout(this)
        val padding = dp(20)
        container.setPadding(padding, padding, padding, 0)
        container.addView(input)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.settings_title))
            .setView(container)
            .setPositiveButton(getString(R.string.btn_load_list)) { dialog, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    listUrl = newUrl
                    loadListFromUrl(newUrl)
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showAddLinkDialog() {
        val input = EditText(this)
        input.hint = getString(R.string.hint_direct_url)
        input.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        input.setHintTextColor(ContextCompat.getColor(this, R.color.text_secondary))

        val container = FrameLayout(this)
        val padding = dp(20)
        container.setPadding(padding, padding, padding, 0)
        container.addView(input)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.btn_add_link))
            .setView(container)
            .setPositiveButton(getString(R.string.btn_play_direct)) { dialog, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) openPlayer(url)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
