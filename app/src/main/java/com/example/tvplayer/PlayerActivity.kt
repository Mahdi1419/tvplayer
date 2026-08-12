package com.example.tvplayer

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
    }

    private lateinit var playerView: PlayerView
    private lateinit var overlayControls: View
    private lateinit var bufferingIndicator: ProgressBar
    private var player: ExoPlayer? = null

    // نگهدارنده‌ی هر گزینه در دیالوگ انتخاب تراک
    private data class TrackOption(val label: String, val group: Tracks.Group?, val trackIndex: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        overlayControls = findViewById(R.id.overlayControls)
        bufferingIndicator = findViewById(R.id.bufferingIndicator)

        // دکمه‌های صدا/زیرنویس فقط همزمان با کنترل‌های خود پلیر نمایش داده می‌شوند
        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                overlayControls.visibility = visibility
            }
        )

        findViewById<Button>(R.id.btnAudioTrack).setOnClickListener {
            showTrackSelectionDialog(C.TRACK_TYPE_AUDIO, getString(R.string.btn_audio_track))
        }
        findViewById<Button>(R.id.btnSubtitleTrack).setOnClickListener {
            showTrackSelectionDialog(C.TRACK_TYPE_TEXT, getString(R.string.btn_subtitle_track))
        }

        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrEmpty()) {
            finish()
            return
        }
        initPlayer(url)
    }

    private fun initPlayer(url: String) {
        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        playerView.player = exoPlayer

        exoPlayer.setMediaItem(MediaItem.fromUri(url))

        // ترجیح انتخاب خودکار زیرنویس در صورت وجود در فایل
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setSelectUndeterminedTextLanguage(true)
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                bufferingIndicator.visibility =
                    if (playbackState == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@PlayerActivity,
                    "خطا در پخش ویدیو: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        })

        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun showTrackSelectionDialog(trackType: Int, title: String) {
        val exoPlayer = player ?: return
        val tracks: Tracks = exoPlayer.currentTracks

        val options = mutableListOf<TrackOption>()
        options.add(TrackOption(getString(R.string.auto_default), null, -1))

        for (group in tracks.groups) {
            if (group.type != trackType) continue
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val lang = format.language ?: "?"
                val name = if (!format.label.isNullOrEmpty()) format.label!! else lang
                options.add(TrackOption("$name ($lang)", group, i))
            }
        }

        if (options.size == 1) {
            options.add(TrackOption(getString(R.string.no_track_found), null, -2))
        }

        val labels = options.map { it.label }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(labels) { dialog, which ->
                val chosen = options[which]
                when {
                    chosen.trackIndex == -1 -> {
                        val params = exoPlayer.trackSelectionParameters.buildUpon()
                            .clearOverridesOfType(trackType)
                            .setTrackTypeDisabled(trackType, false)
                            .build()
                        exoPlayer.trackSelectionParameters = params
                    }
                    chosen.trackIndex >= 0 && chosen.group != null -> {
                        val override = TrackSelectionOverride(chosen.group.mediaTrackGroup, chosen.trackIndex)
                        val params = exoPlayer.trackSelectionParameters.buildUpon()
                            .setOverrideForType(override)
                            .setTrackTypeDisabled(trackType, false)
                            .build()
                        exoPlayer.trackSelectionParameters = params
                    }
                    // chosen.trackIndex == -2 یعنی "موردی یافت نشد" -> کاری انجام نمی‌شود
                }
                dialog.dismiss()
            }
            .show()
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }
}
