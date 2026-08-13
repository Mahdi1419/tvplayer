package com.example.tvplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import java.util.concurrent.TimeUnit

class VideoAdapter(
    private val onClick: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    private val items = mutableListOf<VideoItem>()

    fun submitList(newItems: List<VideoItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount() = items.size

    class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.card)
        private val thumbnail: ImageView = itemView.findViewById(R.id.imgThumbnail)
        private val title: TextView = itemView.findViewById(R.id.txtTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.txtSubtitle)
        private val duration: TextView = itemView.findViewById(R.id.txtDuration)
        private val badge: TextView = itemView.findViewById(R.id.txtBadge)

        fun bind(item: VideoItem, onClick: (VideoItem) -> Unit) {
            title.text = item.title
            subtitle.text = if (item.isLocal) "ذخیره‌شده روی دستگاه" else item.url
            badge.text = if (item.isLocal) itemView.context.getString(R.string.badge_local)
                         else itemView.context.getString(R.string.badge_online)

            if (item.durationMs > 0) {
                duration.visibility = View.VISIBLE
                duration.text = formatDuration(item.durationMs)
            } else {
                duration.visibility = View.GONE
            }

            val placeholder = if (item.isLocal)
                R.drawable.ic_local_video_placeholder
            else
                R.drawable.ic_online_video_placeholder

            if (item.isLocal) {
                Glide.with(itemView)
                    .load(item.url)
                    .placeholder(placeholder)
                    .error(placeholder)
                    .centerCrop()
                    .into(thumbnail)
            } else {
                Glide.with(itemView).clear(thumbnail)
                thumbnail.setImageResource(placeholder)
            }

            card.setOnClickListener { onClick(item) }

            // Lightweight TV focus state: no animations, only a clear focus ring.
            card.isFocusable = true
            card.isFocusableInTouchMode = true
            card.setOnFocusChangeListener { _, hasFocus ->
                card.strokeWidth = if (hasFocus) dp(itemView, 2) else 0
                card.cardElevation = 0f
            }
        }

        private fun dp(view: View, value: Int): Int =
            (value * view.resources.displayMetrics.density).toInt()

        private fun formatDuration(ms: Long): String {
            val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
                   else String.format("%d:%02d", m, s)
        }
    }
}
