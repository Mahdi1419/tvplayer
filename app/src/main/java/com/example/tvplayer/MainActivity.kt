package com.example.tvplayer

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    // ← آدرس پیش‌فرض فایل txt حاوی لینک‌های ویدیو را اینجا تنظیم کنید
    private val defaultListUrl = "https://example.com/videos.txt"

    private lateinit var listView: ListView
    private lateinit var edtListUrl: EditText
    private lateinit var edtDirectUrl: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var txtStatus: TextView
    private lateinit var adapter: ArrayAdapter<String>

    private val items = mutableListOf<VideoItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.listView)
        edtListUrl = findViewById(R.id.edtListUrl)
        edtDirectUrl = findViewById(R.id.edtDirectUrl)
        progressBar = findViewById(R.id.progressBar)
        txtStatus = findViewById(R.id.txtStatus)

        edtListUrl.setText(defaultListUrl)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            openPlayer(items[position].url)
        }

        findViewById<Button>(R.id.btnLoadList).setOnClickListener {
            val url = edtListUrl.text.toString().trim()
            if (url.isNotEmpty()) loadListFromUrl(url)
        }

        findViewById<Button>(R.id.btnPlayDirect).setOnClickListener {
            val url = edtDirectUrl.text.toString().trim()
            if (url.isNotEmpty()) openPlayer(url)
        }

        // در شروع برنامه به صورت خودکار لیست پیش‌فرض بارگیری می‌شود
        loadListFromUrl(defaultListUrl)
    }

    private fun openPlayer(url: String) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra(PlayerActivity.EXTRA_URL, url)
        startActivity(intent)
    }

    private fun loadListFromUrl(urlString: String) {
        progressBar.visibility = ProgressBar.VISIBLE
        txtStatus.text = "در حال دریافت لیست..."

        Thread {
            try {
                val text = downloadText(urlString)
                val parsed = parseVideoList(text)
                Handler(Looper.getMainLooper()).post {
                    items.clear()
                    items.addAll(parsed)
                    adapter.clear()
                    adapter.addAll(parsed.map { it.title })
                    adapter.notifyDataSetChanged()
                    progressBar.visibility = ProgressBar.GONE
                    txtStatus.text = if (parsed.isEmpty())
                        "لینکی در فایل پیدا نشد"
                    else
                        "${parsed.size} لینک بارگیری شد"
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    progressBar.visibility = ProgressBar.GONE
                    txtStatus.text = "خطا در دریافت لیست: ${e.message}"
                }
            }
        }.start()
    }

    private fun downloadText(urlString: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.requestMethod = "GET"
        conn.connect()

        if (conn.responseCode !in 200..299) {
            throw Exception("HTTP ${conn.responseCode}")
        }

        val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line).append('\n')
        }
        reader.close()
        conn.disconnect()
        return sb.toString()
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
}
