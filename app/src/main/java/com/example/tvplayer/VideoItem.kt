package com.example.tvplayer

data class VideoItem(
    val title: String,
    val url: String,
    val isLocal: Boolean = false,
    val durationMs: Long = 0
)
